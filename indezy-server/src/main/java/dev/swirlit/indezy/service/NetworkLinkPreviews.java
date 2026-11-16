package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.NetworkLinkPreview;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.scraping.LinkPreviewReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The preview cards of the posts' links (see docs/features.md#member-network): once a post with a link is saved, its
 * first link is read in the background, never in the request, and its card kept with the post while its text still names
 * that link; a link out of reach leaves no card.
 */
@Component
@Slf4j
public class NetworkLinkPreviews {

    /** A web address in a text, up to the first space or closing bracket; its trailing punctuation is not part of it. */
    private static final Pattern LINK = Pattern.compile("https?://[^\\s<>\"')\\]]+");

    private final LinkPreviewReader reader;
    private final NetworkPostRepository posts;
    private final TransactionTemplate transactions;
    private final TaskExecutor executor;

    public NetworkLinkPreviews(LinkPreviewReader reader, NetworkPostRepository posts, TransactionTemplate transactions,
                               @Qualifier("applicationTaskExecutor") TaskExecutor executor) {
        this.reader = reader;
        this.posts = posts;
        this.transactions = transactions;
        this.executor = executor;
    }

    /** The first link of a text; none without one. */
    static Optional<String> firstLink(String text) {
        Matcher found = LINK.matcher(text == null ? "" : text);
        return found.find() ? Optional.of(found.group().replaceAll("[.,;:!?]+$", "")) : Optional.empty();
    }

    /** Reads the card of the post's first link once its transaction commits; drops the card when the text names none. */
    void refresh(NetworkPost post) {
        Optional<String> link = firstLink(post.getText());
        if (link.isEmpty()) {
            post.setPreview(null);
            return;
        }
        if (post.getPreview() != null && link.get().equals(post.getPreview().getUrl())) {
            return;
        }
        post.setPreview(null);
        Long postId = post.getId();
        Runnable read = () -> executor.execute(() -> attach(postId, link.get()));
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
        try {
            reader.read(url).ifPresent(preview -> transactions.executeWithoutResult(status -> posts.findById(postId)
                .filter(post -> firstLink(post.getText()).filter(url::equals).isPresent()).ifPresent(post -> {
                    NetworkLinkPreview card = new NetworkLinkPreview();
                    card.setUrl(preview.url());
                    card.setSite(preview.site());
                    card.setTitle(preview.title());
                    card.setDescription(preview.description());
                    post.setPreview(card);
                })));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.debug("Post {}'s link could not be previewed: {}", postId, e.getClass().getSimpleName());
        }
    }
}
