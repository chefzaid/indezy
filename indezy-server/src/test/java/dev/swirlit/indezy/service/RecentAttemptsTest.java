package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RecentAttemptsTest {

    private final Instant now = Instant.parse("2026-10-27T10:00:00Z");

    @Test
    void allowsSoManyAttemptsPerKeyInTheWindow() {
        RecentAttempts attempts = new RecentAttempts(2, Duration.ofHours(1), 100);
        assertThat(attempts.tryAcquire("1.2.3.4", now)).isTrue();
        assertThat(attempts.tryAcquire("1.2.3.4", now.plusSeconds(60))).isTrue();
        assertThat(attempts.tryAcquire("1.2.3.4", now.plusSeconds(120))).isFalse();
        assertThat(attempts.tryAcquire("5.6.7.8", now.plusSeconds(120))).isTrue();
        assertThat(attempts.tryAcquire(null, now)).isTrue();
        // An hour after the first attempt, it no longer counts.
        assertThat(attempts.tryAcquire("1.2.3.4", now.plus(Duration.ofMinutes(61)))).isTrue();
        assertThat(attempts.tryAcquire("1.2.3.4", now.plus(Duration.ofMinutes(61)))).isFalse();
    }

    @Test
    void forgetsIdleKeysPastTheLimit() {
        RecentAttempts attempts = new RecentAttempts(5, Duration.ofHours(1), 3);
        for (int i = 0; i < 4; i++) {
            attempts.tryAcquire("old-" + i, now);
        }
        attempts.tryAcquire("recent", now.plus(Duration.ofMinutes(30)));
        assertThat(attempts.size()).isEqualTo(5);
        attempts.tryAcquire("new", now.plus(Duration.ofMinutes(90)));
        // The four idle for an hour are gone; the recent one and the new one stay.
        assertThat(attempts.size()).isEqualTo(2);
    }
}
