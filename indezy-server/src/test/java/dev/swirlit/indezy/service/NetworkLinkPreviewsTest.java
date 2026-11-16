package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.NetworkLinkPreview;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.scraping.LinkPreviewReader;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkLinkPreviewsTest {

    private final LinkPreviewReader reader = mock(LinkPreviewReader.class);
    private final NetworkPostRepository posts = mock(NetworkPostRepository.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final NetworkLinkPreviews previews = new NetworkLinkPreviews(reader, posts, transactions, new SyncTaskExecutor());

    private static NetworkPost post(String text) {
        NetworkPost post = new NetworkPost();
        post.setId(7L);
        post.setText(text);
        return post;
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsTheFirstLinkAndKeepsItsCardWhileThePostStillNamesIt() throws Exception {
        assertThat(NetworkLinkPreviews.firstLink("Lu sur https://blog.example.org/kafka, puis (https://autre.example.org).")).contains(
            "https://blog.example.org/kafka");
        assertThat(NetworkLinkPreviews.firstLink("Pas de lien")).isEmpty();
        assertThat(NetworkLinkPreviews.firstLink(null)).isEmpty();
        doAnswer(call -> {
            ((Consumer<Object>) call.getArgument(0)).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        NetworkPost post = post("Lu sur https://blog.example.org/kafka.");
        when(posts.findById(7L)).thenReturn(Optional.of(post));
        when(reader.read("https://blog.example.org/kafka")).thenReturn(Optional.of(new LinkPreviewReader.Preview(
            "https://blog.example.org/kafka", "Le blog", "Kafka en prod", "Un retour")));

        previews.refresh(post);
        assertThat(post.getPreview().getTitle()).isEqualTo("Kafka en prod");
        assertThat(post.getPreview().getSite()).isEqualTo("Le blog");

        // The same link is not read again; another text without a link drops the card.
        previews.refresh(post);
        verify(reader).read("https://blog.example.org/kafka");
        post.setText("Plus de lien");
        previews.refresh(post);
        assertThat(post.getPreview()).isNull();
    }

    @Test
    void keepsNoCardWhenThePostNoLongerNamesTheLinkOrItCannotBeRead() throws Exception {
        NetworkPost post = post("Changé");
        when(posts.findById(7L)).thenReturn(Optional.of(post));
        doAnswer(call -> {
            @SuppressWarnings("unchecked")
            Consumer<Object> work = call.getArgument(0);
            work.accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        when(reader.read("https://blog.example.org/a")).thenReturn(Optional.of(new LinkPreviewReader.Preview("https://blog.example.org/a",
            "Le blog", "A", null)));
        previews.attach(7L, "https://blog.example.org/a");
        assertThat(post.getPreview()).isNull();

        when(reader.read("https://blog.example.org/b")).thenThrow(new IllegalStateException("broken"));
        previews.attach(7L, "https://blog.example.org/b");
        when(reader.read("https://blog.example.org/c")).thenReturn(Optional.empty());
        previews.attach(7L, "https://blog.example.org/c");
        verify(transactions, never()).execute(any());
        NetworkLinkPreview kept = new NetworkLinkPreview();
        kept.setUrl("https://blog.example.org/a");
        post.setPreview(kept);
        post.setText("Encore https://blog.example.org/a");
        previews.refresh(post);
        assertThat(post.getPreview()).isSameAs(kept);
    }
}
