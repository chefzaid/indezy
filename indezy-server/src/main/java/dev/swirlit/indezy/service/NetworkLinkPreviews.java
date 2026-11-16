package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkLinkPreview;
import dev.swirlit.indezy.model.NetworkMessage;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.repository.NetworkMessageRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.scraping.LinkPreviewReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The preview cards of the links of posts and messages (see docs/features.md#member-network): once a post or a message
 * with a link is saved, its first link is read in the background, never in the request, and its card kept with it while
 * its text still names that link; a link out of reach leaves no card.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NetworkLinkPreviews {

    /** A web address in a text, up to the first space or closing bracket; its trailing punctuation is not part of it. */
    private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\"')\\]]+");

    private final LinkPreviewReader reader;
    private final NetworkPostRepository posts;
    private final NetworkMessageRepository messages;
    private final TransactionTemplate transactions;
    /** Spring Boot's task executor, found by this name among the executors. */
    private final TaskExecutor applicationTaskExecutor;

    /** The first link of a text; none without one. */
    static Optional<String> firstLink(String text) {
        Matcher found = LINK.matcher(text == null ? "" : text);
        return found.find() ? Optional.of(found.group().replaceAll("[.,;:!?]+$", "")) : Optional.empty();
    }

    /** The card shown for a kept preview; none before its page was read. */
    static NetworkDtos.LinkPreview view(NetworkLinkPreview card) {
        return card == null || card.getTitle() == null ? null : new NetworkDtos.LinkPreview(card.getUrl(), card.getSite(), card.getTitle(),
            card.getDescription());
    }

    /** Reads the card of the post's first link once its transaction commits; drops the card when the text names none. */
    void refresh(NetworkPost post) {
        Long postId = post.getId();
        refresh(post.getText(), post.getPreview(), post::setPreview, url -> attach(postId, url));
    }

    /** Reads the card of the message's first link once its transaction commits; drops the card when the text names none. */
    void refresh(NetworkMessage message) {
        Long messageId = message.getId();
        refresh(message.getText(), message.getPreview(), message::setPreview, url -> attachToMessage(messageId, url));
    }

    private void refresh(String text, NetworkLinkPreview current, Consumer<NetworkLinkPreview> keep, Consumer<String> attach) {
        Optional<String> link = firstLink(text);
        if (link.isEmpty()) {
            keep.accept(null);
            return;
        }
        if (current != null && link.get().equals(current.getUrl())) {
            return;
        }
        keep.accept(null);
        Runnable read = () -> applicationTaskExecutor.execute(() -> attach.accept(link.get()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    read.run();
                }
            });
        } else {
            read.run();
        }
    }

    /** Reads the link and keeps its card with the post, while the post still names it. */
    void attach(Long postId, String url) {
        attach(url, card -> posts.findById(postId).filter(post -> names(post.getText(), url)).ifPresent(post -> post.setPreview(card)),
            "Post " + postId);
    }

    /** Reads the link and keeps its card with the message, while the message is still there and names it. */
    void attachToMessage(Long messageId, String url) {
        attach(url, card -> messages.findById(messageId).filter(message -> !message.isDeleted() && names(message.getText(), url))
            .ifPresent(message -> message.setPreview(card)), "Message " + messageId);
    }

    private static boolean names(String text, String url) {
        return firstLink(text).filter(url::equals).isPresent();
    }

    private void attach(String url, Consumer<NetworkLinkPreview> keep, String what) {
        try {
            reader.read(url).ifPresent(preview -> transactions.executeWithoutResult(status -> {
                NetworkLinkPreview card = new NetworkLinkPreview();
                card.setUrl(preview.url());
                card.setSite(preview.site());
                card.setTitle(preview.title());
                card.setDescription(preview.description());
                keep.accept(card);
            }));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.debug("{}'s link could not be previewed: {}", what, e.getClass().getSimpleName());
        }
    }
}
