package dev.swirlit.indezy.service;

import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.repository.NetworkCommentRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
    static final int REPORTS_PER_HOUR = 20;
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final DateTimeFormatter SUSPENSION_END = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH)
        .withZone(ZoneId.of("Europe/Paris"));

    private final NetworkPostRepository posts;
    private final NetworkCommentRepository comments;
    private final NetworkFollowRepository follows;
    private final NetworkReportRepository reports;
    private final NetworkMemberRepository members;
    private final Clock clock;

    void post(Long userId) {
        notSuspended(userId);
        if (posts.countByAuthorIdAndCreatedAtAfter(userId, hourAgo()) >= POSTS_PER_HOUR) {
            throw new TooManyRequestsException("Too many posts this hour: try again later", WINDOW);
        }
    }

    void comment(Long userId) {
        notSuspended(userId);
        if (comments.countByAuthorIdAndCreatedAtAfter(userId, hourAgo()) >= COMMENTS_PER_HOUR) {
            throw new TooManyRequestsException("Too many comments this hour: try again later", WINDOW);
        }
    }

    void follow(Long userId) {
        notSuspended(userId);
        if (follows.countByFollowerIdAndCreatedAtAfter(userId, hourAgo()) >= FOLLOWS_PER_HOUR) {
            throw new TooManyRequestsException("Too many members followed this hour: try again later", WINDOW);
        }
    }

    void report(Long userId) {
        notSuspended(userId);
        if (reports.countByReporterIdAndCreatedAtAfter(userId, hourAgo()) >= REPORTS_PER_HOUR) {
            throw new TooManyRequestsException("Too many reports this hour: try again later", WINDOW);
        }
    }

    /** Refuses a member the moderators suspended, until the suspension ends. */
    private void notSuspended(Long userId) {
        Instant until = members.findByUserId(userId).map(NetworkMember::getSuspendedUntil).orElse(null);
        if (until != null && until.isAfter(clock.instant())) {
            throw new InvalidStateException("The moderators suspended you until " + SUSPENSION_END.format(until));
        }
    }

    /** An hour ago, in the zone the rows' creation times are written in. */
    private LocalDateTime hourAgo() {
        return LocalDateTime.now(clock.withZone(ZoneId.systemDefault())).minus(WINDOW);
    }
}
