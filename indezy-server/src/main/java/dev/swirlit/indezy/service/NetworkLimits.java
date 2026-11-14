package dev.swirlit.indezy.service;

import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.repository.NetworkCommentRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * How much a member may post, comment and follow in an hour (see docs/security.md#member-network), so that one account
 * cannot flood the network: beyond, the request answers 429 with the time to wait.
 */
@Component
@RequiredArgsConstructor
public class NetworkLimits {

    static final int POSTS_PER_HOUR = 10;
    static final int COMMENTS_PER_HOUR = 60;
    static final int FOLLOWS_PER_HOUR = 100;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final NetworkPostRepository posts;
    private final NetworkCommentRepository comments;
    private final NetworkFollowRepository follows;
    private final Clock clock;

    void post(Long userId) {
        if (posts.countByAuthorIdAndCreatedAtAfter(userId, hourAgo()) >= POSTS_PER_HOUR) {
            throw new TooManyRequestsException("Too many posts this hour: try again later", WINDOW);
        }
    }

    void comment(Long userId) {
        if (comments.countByAuthorIdAndCreatedAtAfter(userId, hourAgo()) >= COMMENTS_PER_HOUR) {
            throw new TooManyRequestsException("Too many comments this hour: try again later", WINDOW);
        }
    }

    void follow(Long userId) {
        if (follows.countByFollowerIdAndCreatedAtAfter(userId, hourAgo()) >= FOLLOWS_PER_HOUR) {
            throw new TooManyRequestsException("Too many members followed this hour: try again later", WINDOW);
        }
    }

    /** An hour ago, in the zone the rows' creation times are written in. */
    private LocalDateTime hourAgo() {
        return LocalDateTime.now(clock.withZone(ZoneId.systemDefault())).minus(WINDOW);
    }
}
