package dev.swirlit.indezy.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one client address from creating accounts or asking for emails without end (see docs/security.md): registering,
 * asking for a reset link and asking for the confirmation link again count together, {@value #MAX_REQUESTS} an hour per
 * address at most by default; in memory, per replica.
 */
@Service
public class AccountRequestLimiter {

    static final int MAX_REQUESTS = 10;
    static final Duration WINDOW = Duration.ofHours(1);
    /** Past this many addresses tracked, the expired windows are dropped. */
    private static final int TRIM_AT = 10_000;

    private record Window(Instant start, int count) {
    }

    private final Clock clock;
    private final int maxRequests;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /** {@code indezy.auth.requests-per-hour}: {@value #MAX_REQUESTS} by default; the tests, all from one address, raise it. */
    @Autowired
    public AccountRequestLimiter(@Value("${indezy.auth.requests-per-hour:" + MAX_REQUESTS + "}") int maxRequests) {
        this(Clock.systemUTC(), maxRequests);
    }

    public AccountRequestLimiter() {
        this(Clock.systemUTC(), MAX_REQUESTS);
    }

    AccountRequestLimiter(Clock clock) {
        this(clock, MAX_REQUESTS);
    }

    AccountRequestLimiter(Clock clock, int maxRequests) {
        this.clock = clock;
        this.maxRequests = maxRequests;
    }

    /** Counts a request from {@code address}; whether it may go on. */
    public boolean allow(String address) {
        Instant now = clock.instant();
        if (windows.size() > TRIM_AT) {
            windows.values().removeIf(window -> window.start().plus(WINDOW).isBefore(now));
        }
        Window window = windows.compute(address == null ? "" : address, (key, kept) ->
            kept == null || kept.start().plus(WINDOW).isBefore(now) ? new Window(now, 1) : new Window(kept.start(), kept.count() + 1));
        return window.count() <= maxRequests;
    }
}
