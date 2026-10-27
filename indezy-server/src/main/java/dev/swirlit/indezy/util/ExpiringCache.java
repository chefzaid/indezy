package dev.swirlit.indezy.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Values kept in memory for a while, one per key, then read again: an {@link ExpiringValue} per key. A key's value is
 * read once however many requests ask for it at the same time, and at most {@code maxKeys} keys are kept: past that,
 * the expired ones are dropped, and if none expired, every one.
 *
 * @param <K> the key, compared by {@code equals}
 * @param <V> the value, never changed once read
 */
public final class ExpiringCache<K, V> {

    /** The value and when it was read. */
    private record Read<V>(V value, Instant at) {
    }

    private final Duration keepFor;
    private final int maxKeys;
    private final Map<K, Read<V>> reads = new ConcurrentHashMap<>();

    public ExpiringCache(Duration keepFor, int maxKeys) {
        this.keepFor = keepFor;
        this.maxKeys = maxKeys;
    }

    /** The key's value read less than the period before {@code now}, else read again with {@code reader}. */
    public V get(K key, Instant now, Function<K, V> reader) {
        Read<V> current = reads.get(key);
        if (current != null && isFresh(current, now)) {
            return current.value();
        }
        if (reads.size() >= maxKeys) {
            reads.values().removeIf(read -> !isFresh(read, now));
            if (reads.size() >= maxKeys) {
                reads.clear();
            }
        }
        return reads.compute(key, (k, read) -> read != null && isFresh(read, now) ? read : new Read<>(reader.apply(k), now)).value();
    }

    /** Has the next {@link #get} of the keys {@code which} accepts read them again. */
    public void forget(Predicate<K> which) {
        reads.keySet().removeIf(which);
    }

    public int size() {
        return reads.size();
    }

    private boolean isFresh(Read<V> read, Instant now) {
        return read.at().plus(keepFor).isAfter(now);
    }
}
