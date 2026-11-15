package dev.swirlit.indezy.service;

import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.repository.NetworkCommentRepository;
import dev.swirlit.indezy.repository.NetworkFollowRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkReportRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkLimitsTest {

    private final NetworkPostRepository posts = mock(NetworkPostRepository.class);
    private final NetworkCommentRepository comments = mock(NetworkCommentRepository.class);
    private final NetworkFollowRepository follows = mock(NetworkFollowRepository.class);
    private final NetworkReportRepository reports = mock(NetworkReportRepository.class);
    private final NetworkMemberRepository members = mock(NetworkMemberRepository.class);
    private final Instant now = Instant.parse("2026-11-14T10:00:00Z");
    private final NetworkLimits limits = new NetworkLimits(posts, comments, follows, reports, members, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void refusesBeyondTheHourlyLimitsAndCountsTheLastHourOnly() {
        when(posts.countByAuthorIdAndCreatedAtAfter(eq(1L), any())).thenReturn((long) NetworkLimits.POSTS_PER_HOUR - 1, (long) NetworkLimits.POSTS_PER_HOUR);
        assertThatCode(() -> limits.post(1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limits.post(1L)).isInstanceOf(TooManyRequestsException.class).hasMessageContaining("posts");
        verify(posts, org.mockito.Mockito.atLeastOnce())
            .countByAuthorIdAndCreatedAtAfter(1L, LocalDateTime.ofInstant(now, ZoneId.systemDefault()).minusHours(1));

        when(comments.countByAuthorIdAndCreatedAtAfter(eq(1L), any())).thenReturn((long) NetworkLimits.COMMENTS_PER_HOUR);
        assertThatThrownBy(() -> limits.comment(1L)).isInstanceOf(TooManyRequestsException.class).hasMessageContaining("comments");
        when(follows.countByFollowerIdAndCreatedAtAfter(eq(1L), any())).thenReturn((long) NetworkLimits.FOLLOWS_PER_HOUR - 1, 1_000L);
        assertThatCode(() -> limits.follow(1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limits.follow(1L)).isInstanceOf(TooManyRequestsException.class).hasMessageContaining("followed");
    }

    @Test
    void refusesTooManyReportsAndASuspendedMemberUntilTheSuspensionEnds() {
        when(reports.countByReporterIdAndCreatedAtAfter(eq(1L), any())).thenReturn((long) NetworkLimits.REPORTS_PER_HOUR - 1,
            (long) NetworkLimits.REPORTS_PER_HOUR);
        assertThatCode(() -> limits.report(1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limits.report(1L)).isInstanceOf(TooManyRequestsException.class).hasMessageContaining("reports");

        NetworkMember suspended = new NetworkMember();
        suspended.setSuspendedUntil(now.plusSeconds(3600));
        when(members.findByUserId(2L)).thenReturn(Optional.of(suspended));
        assertThatThrownBy(() -> limits.post(2L)).isInstanceOf(InvalidStateException.class)
            .hasMessage("The moderators suspended you until 14 November 2026, 12:00");
        assertThatThrownBy(() -> limits.comment(2L)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> limits.follow(2L)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> limits.report(2L)).isInstanceOf(InvalidStateException.class);
        suspended.setSuspendedUntil(now);
        assertThatCode(() -> limits.post(2L)).doesNotThrowAnyException();
    }
}
