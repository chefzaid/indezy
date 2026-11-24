package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AccountRequestLimiterTest {

    @Test
    void allowsTenRequestsAnHourPerAddress() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-24T10:00:00Z"));
        AccountRequestLimiter limiter = new AccountRequestLimiter(new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        });
        for (int request = 0; request < AccountRequestLimiter.MAX_REQUESTS; request++) {
            assertThat(limiter.allow("203.0.113.7")).isTrue();
        }
        assertThat(limiter.allow("203.0.113.7")).isFalse();
        assertThat(limiter.allow("198.51.100.2")).isTrue();
        now.set(now.get().plus(Duration.ofMinutes(61)));
        assertThat(limiter.allow("203.0.113.7")).isTrue();
    }
}
