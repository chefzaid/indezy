package dev.swirlit.indezy.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts attempts per key (a client address) over a sliding window, in memory. Keys idle for a whole window are
 * forgotten once more than {@code kept} are held, so many addresses cannot make it grow without end.
 */
final class RecentAttempts {

    private final int allowed;
    private final Duration window;
    private final int kept;
    private final Map<String, Deque<Instant>> byKey = new ConcurrentHashMap<>();

    RecentAttempts(int allowed, Duration window, int kept) {
        this.allowed = allowed;
        this.window = window;
        this.kept = kept;
    }

    /** Records an attempt at {@code now}, unless {@code allowed} were already made within the window: then false. */
    boolean tryAcquire(String key, Instant now) {
        Instant since = now.minus(window);
        if (byKey.size() > kept) {
            byKey.values().removeIf(times -> {
                synchronized (times) {
                    return times.isEmpty() || times.peekLast().isBefore(since);
                }
            });
        }
        Deque<Instant> times = byKey.computeIfAbsent(key == null ? "" : key, unused -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(since)) {
                times.pollFirst();
            }
            if (times.size() >= allowed) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    int size() {
        return byKey.size();
    }
}
