package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.NetworkPost;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NetworkForYouServiceTest {

    private static NetworkPost tagged(String... tags) {
        NetworkPost post = new NetworkPost();
        post.setTags(Set.of(tags));
        return post;
    }

    @Test
    void scoresWhatThePostSharesWithTheReaderAndLowersItWithAge() {
        NetworkForYouService.Reader reader = new NetworkForYouService.Reader(Set.of("kafka"), Set.of("java"), "lyon");
        assertThat(NetworkForYouService.score(tagged("kafka", "java"), reader, "Lyon", 0, 0)).isEqualTo(7);
        // One a reaction, five at most.
        assertThat(NetworkForYouService.score(tagged(), reader, null, 12, 0)).isEqualTo(5);
        assertThat(NetworkForYouService.score(tagged(), reader, null, 3, 0)).isEqualTo(3);
        assertThat(NetworkForYouService.score(tagged("kafka"), reader, "Paris", 0, 3)).isCloseTo(1.5, within(1e-9));
        assertThat(NetworkForYouService.score(tagged("python"), new NetworkForYouService.Reader(Set.of(), Set.of(), null), "Lyon", 0, -1))
            .isZero();
    }
}
