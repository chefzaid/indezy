package dev.swirlit.indezy.util;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * A value kept in memory for a while, then read again: what many requests ask at once (the feed's site counts, the
 * rate medians) is read once per period instead of once per request. Requests arriving while it is read again wait for
 * that one reading rather than each reading it.
 *
 * @param <T> the value, never changed once read
 */
public final class ExpiringValue<T> {

    /** The value and when it was read. */
    private record Read<T>(T value, Instant at) {
    }

    private final Duration keepFor;
    private final Supplier<T> reader;
    private volatile Read<T> read;

    public ExpiringValue(Duration keepFor, Supplier<T> reader) {
        this.keepFor = keepFor;
        this.reader = reader;
    }

    /** The value read less than the period before {@code now}, else read again. */
    public T get(Instant now) {
        Read<T> current = read;
        if (current != null && isFresh(current, now)) {
            return current.value();
        }
        synchronized (this) {
            current = read;
            if (current == null || !isFresh(current, now)) {
                current = new Read<>(reader.get(), now);
                read = current;
            }
            return current.value();
        }
    }

    /** Has the next {@link #get} read the value again. */
    public void forget() {
        read = null;
    }

    private boolean isFresh(Read<T> current, Instant now) {
        return current.at().plus(keepFor).isAfter(now);
    }
}
