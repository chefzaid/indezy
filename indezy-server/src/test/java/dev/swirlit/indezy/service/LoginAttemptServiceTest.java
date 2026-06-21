package dev.swirlit.indezy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    /** A clock the test can advance to exercise the lockout window. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        service = new LoginAttemptService(clock);
    }

    @Test
    void isBlocked_ShouldBeFalseForUnknownAccount() {
        assertThat(service.isBlocked("user@example.com")).isFalse();
    }

    @Test
    void isBlocked_ShouldBeFalseBelowTheAttemptLimit() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS - 1; i++) {
            service.loginFailed("user@example.com");
        }
        assertThat(service.isBlocked("user@example.com")).isFalse();
    }

    @Test
    void isBlocked_ShouldBeTrueOnceTheAttemptLimitIsReached() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.loginFailed("user@example.com");
        }
        assertThat(service.isBlocked("user@example.com")).isTrue();
        // Keying is case-insensitive, so the same account is blocked regardless of casing.
        assertThat(service.isBlocked("USER@example.com")).isTrue();
    }

    @Test
    void loginSucceeded_ShouldClearFailures() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.loginFailed("user@example.com");
        }

        service.loginSucceeded("user@example.com");

        assertThat(service.isBlocked("user@example.com")).isFalse();
    }

    @Test
    void isBlocked_ShouldResetAfterTheLockoutWindowExpires() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.loginFailed("user@example.com");
        }
        assertThat(service.isBlocked("user@example.com")).isTrue();

        clock.advance(LoginAttemptService.LOCKOUT.plusMinutes(1));

        assertThat(service.isBlocked("user@example.com")).isFalse();
    }
}
