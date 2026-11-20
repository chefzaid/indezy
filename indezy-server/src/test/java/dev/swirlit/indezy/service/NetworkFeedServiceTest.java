package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.repository.NetworkCommunityMemberRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkMissionTakeRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
import dev.swirlit.indezy.repository.NetworkTagFollowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkFeedServiceTest {

    private static final long READER = 1L;
    private static final NetworkDtos.MemberCard CARD = new NetworkDtos.MemberCard("leo", "Léo", "Martin", null, null, List.of(), null,
        false, null, null, List.of(), false);

    private final NetworkPostService postService = mock(NetworkPostService.class);
    private final NetworkFollowService follows = mock(NetworkFollowService.class);
    private final NetworkPostRepository posts = mock(NetworkPostRepository.class);
    private final NetworkRestrictionRepository restrictions = mock(NetworkRestrictionRepository.class);
    private final NetworkFeedService service = new NetworkFeedService(mock(NetworkService.class), postService, follows, posts,
        mock(NetworkFollowRepository.class), restrictions, mock(NetworkTagFollowRepository.class), mock(NetworkCommunityMemberRepository.class),
        mock(NetworkMissionTakeRepository.class), Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC));

    private static NetworkPost post(long author, String... tags) {
        NetworkPost post = new NetworkPost();
        post.setAuthorId(author);
        post.setTags(Set.of(tags));
        return post;
    }

    @Test
    void countsTheTagsOfTheWeeksPostsTheReaderCanReadTheMostNamedFirst() {
        when(restrictions.blockedEitherWay(READER)).thenReturn(List.of(3L));
        when(follows.locked(READER)).thenReturn(Set.of(4L));
        when(postService.authorCard(any())).thenReturn(Optional.of(CARD));
        when(postService.authorCard(5L)).thenReturn(Optional.empty());
        when(posts.recentWithTags(LocalDateTime.of(2026, 11, 9, 10, 0))).thenReturn(List.of(post(2, "kafka", "java"), post(6, "kafka"),
            post(2, "java"), post(7, "lyon"), post(3, "rust", "rust2"), post(4, "rust"), post(5, "rust"), post(5, "go")));

        assertThat(service.trending(READER)).containsExactly(new NetworkDtos.TrendingTag("java", 2), new NetworkDtos.TrendingTag("kafka", 2),
            new NetworkDtos.TrendingTag("lyon", 1));
        verify(postService, never()).authorCard(3L);
    }

    @Test
    void showsEightTagsAtMost() {
        when(postService.authorCard(any())).thenReturn(Optional.of(CARD));
        when(posts.recentWithTags(any())).thenReturn(IntStream.range(0, 12).mapToObj(index -> post(2, "tag" + index)).toList());
        assertThat(service.trending(READER)).hasSize(NetworkFeedService.TRENDING_SHOWN);
    }

    @Test
    void suggestsTheTagsStartingWithWhatWasTypedWithoutPatterns() {
        when(posts.tagsStartingWith(any(), any(Pageable.class))).thenReturn(List.of("kafka"));
        assertThat(service.suggestTags(READER, " #Ka ")).containsExactly("kafka");
        verify(posts).tagsStartingWith("ka%", Pageable.ofSize(NetworkFeedService.SUGGESTED));
        service.suggestTags(READER, "spring_b");
        verify(posts).tagsStartingWith("spring\\_b%", Pageable.ofSize(NetworkFeedService.SUGGESTED));
        assertThat(service.suggestTags(READER, "50%")).isEmpty();
        assertThat(service.suggestTags(READER, null)).containsExactly("kafka");
    }
}
