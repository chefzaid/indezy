package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkPostImpression;
import dev.swirlit.indezy.model.enums.NetworkPostKind;
import dev.swirlit.indezy.repository.NetworkBookmarkRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkMissionTakeRepository;
import dev.swirlit.indezy.repository.NetworkPostImpressionRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Posts' analytics for their authors (see docs/features.md#member-network): the posts a member was shown, reported by
 * their pages, counted once per reading; each post's impressions, members reached and saves; and the author's activity.
 */
@Service
@RequiredArgsConstructor
public class NetworkAnalyticsService {

    static final Duration WEEK = Duration.ofDays(7);
    static final Duration MONTH = Duration.ofDays(30);

    private final NetworkService network;
    private final NetworkPostRepository posts;
    private final NetworkPostImpressionRepository impressions;
    private final NetworkBookmarkRepository bookmarks;
    private final NetworkFollowRepository follows;
    private final NetworkMissionTakeRepository takes;
    private final Clock clock;

    /** The posts a page showed the reader; their own and unknown ones count nothing. */
    @Transactional
    public void shown(Long viewerId, List<Long> postIds) {
        network.requireMember(viewerId);
        Set<Long> asked = postIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (asked.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, NetworkPostImpression> known = impressions.findByViewerIdAndPostIdIn(viewerId, asked).stream()
            .collect(Collectors.toMap(NetworkPostImpression::getPostId, Function.identity()));
        for (NetworkPost post : posts.findAllById(asked)) {
            if (post.getAuthorId().equals(viewerId)) {
                continue;
            }
            NetworkPostImpression impression = known.computeIfAbsent(post.getId(), id -> {
                NetworkPostImpression fresh = new NetworkPostImpression();
                fresh.setPostId(id);
                fresh.setViewerId(viewerId);
                return fresh;
            });
            impression.setTimes(impression.getTimes() + 1);
            impression.setLastSeenAt(now);
            impressions.save(impression);
        }
    }

    /** A post's analytics, for its author alone; another member's post, like an unknown one, is not found. */
    @Transactional(readOnly = true)
    public NetworkDtos.PostStats stats(Long userId, Long postId) {
        network.requireMember(userId);
        NetworkPost post = posts.findByIdAndAuthorId(postId, userId)
            .orElseThrow(() -> new ResourceNotFoundException(NetworkPostService.NOT_FOUND));
        Object[] totals = impressions.totals(post.getId()).stream().findFirst().orElse(new Object[] {0L, 0L});
        return new NetworkDtos.PostStats(((Number) totals[0]).longValue(), ((Number) totals[1]).longValue(), bookmarks.countByPostId(post.getId()));
    }

    /** The author's activity: this week's posts and reach, the last 30 days' followers gained and missions passed on and taken. */
    @Transactional(readOnly = true)
    public NetworkDtos.AuthorActivity activity(Long userId) {
        network.requireMember(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime week = now.minus(WEEK);
        LocalDateTime month = now.minus(MONTH);
        return new NetworkDtos.AuthorActivity(posts.countByAuthorIdAndCreatedAtAfter(userId, week), impressions.reachedSince(userId, week),
            follows.countByFollowedIdAndCreatedAtAfter(userId, month),
            posts.countByAuthorIdAndKindAndCreatedAtAfter(userId, NetworkPostKind.MISSION, month), takes.takenSince(userId, month));
    }
}
