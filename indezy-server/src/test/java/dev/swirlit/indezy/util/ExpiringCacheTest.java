package dev.swirlit.indezy.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiringCacheTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

    private final List<String> reads = new ArrayList<>();
    private final ExpiringCache<String, String> cache = new ExpiringCache<>(Duration.ofMinutes(1), 3);

    private String read(String key) {
        reads.add(key);
        return key.toUpperCase() + reads.size();
    }

    @Test
    void readsEachKeyOncePerPeriod() {
        assertThat(cache.get("a", NOW, this::read)).isEqualTo("A1");
        assertThat(cache.get("a", NOW.plusSeconds(59), this::read)).isEqualTo("A1");
        assertThat(cache.get("b", NOW, this::read)).isEqualTo("B2");
        assertThat(cache.get("a", NOW.plusSeconds(60), this::read)).isEqualTo("A3");
        assertThat(reads).containsExactly("a", "b", "a");
    }

    @Test
    void peeksWithoutReading() {
        assertThat(cache.peek("a", NOW)).isNull();
        cache.get("a", NOW, this::read);
        assertThat(cache.peek("a", NOW.plusSeconds(59))).isEqualTo("A1");
        assertThat(cache.peek("a", NOW.plusSeconds(60))).isNull();
        assertThat(reads).containsExactly("a");
    }

    @Test
    void forgetsTheKeysAskedFor() {
        cache.get("a", NOW, this::read);
        cache.get("b", NOW, this::read);
        cache.forget("a"::equals);
        assertThat(cache.get("a", NOW, this::read)).isEqualTo("A3");
        assertThat(cache.get("b", NOW, this::read)).isEqualTo("B2");
    }

    @Test
    void keepsAtMostItsNumberOfKeys() {
        cache.get("a", NOW, this::read);
        cache.get("b", NOW, this::read);
        cache.get("c", NOW.plusSeconds(30), this::read);
        // Full: the expired ones go first.
        cache.get("d", NOW.plusSeconds(61), this::read);
        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("c", NOW.plusSeconds(61), this::read)).isEqualTo("C3");
        // Full of fresh ones: every one goes.
        cache.get("e", NOW.plusSeconds(62), this::read);
        cache.get("f", NOW.plusSeconds(62), this::read);
        assertThat(cache.size()).isEqualTo(1);
    }
}
