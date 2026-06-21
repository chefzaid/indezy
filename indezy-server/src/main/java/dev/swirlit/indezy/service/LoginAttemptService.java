package dev.swirlit.indezy.service;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force protection for login: tracks failed attempts per account (email) and
 * blocks further attempts once {@link #MAX_ATTEMPTS} failures occur within {@link #LOCKOUT}.
 * A successful login clears the account's failures, and the window resets once it expires.
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, Attempt> attemptsByKey = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** Clears any recorded failures for the account after a successful login. */
    public void loginSucceeded(String key) {
        attemptsByKey.remove(normalize(key));
    }

    /** Records a failed login, starting a fresh lockout window when none is active. */
    public void loginFailed(String key) {
        Instant now = clock.instant();
        attemptsByKey.compute(normalize(key), (k, existing) -> {
            if (existing == null || now.isAfter(existing.windowEnd())) {
                return new Attempt(1, now.plus(LOCKOUT));
            }
            return new Attempt(existing.count() + 1, existing.windowEnd());
        });
    }

    /** True when the account has reached the attempt limit and its lockout window is still active. */
    public boolean isBlocked(String key) {
        String normalized = normalize(key);
        Attempt attempt = attemptsByKey.get(normalized);
        if (attempt == null) {
            return false;
        }
        if (clock.instant().isAfter(attempt.windowEnd())) {
            attemptsByKey.remove(normalized);
            return false;
        }
        return attempt.count() >= MAX_ATTEMPTS;
    }

    private String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private record Attempt(int count, Instant windowEnd) {
    }
}
