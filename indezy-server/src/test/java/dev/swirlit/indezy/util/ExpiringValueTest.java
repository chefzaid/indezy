package dev.swirlit.indezy.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiringValueTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    @Test
    void readsOncePerPeriodAndAgainWhenForgotten() {
        AtomicInteger reads = new AtomicInteger();
        ExpiringValue<Integer> value = new ExpiringValue<>(Duration.ofMinutes(1), reads::incrementAndGet);

        assertThat(value.get(NOW)).isEqualTo(1);
        assertThat(value.get(NOW.plusSeconds(59))).isEqualTo(1);
        assertThat(value.get(NOW.plusSeconds(60))).isEqualTo(2);
        value.forget();
        assertThat(value.get(NOW.plusSeconds(61))).isEqualTo(3);
        assertThat(reads).hasValue(3);
    }

    @Test
    void readsOnceForRequestsArrivingTogether() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch reading = new CountDownLatch(1);
        ExpiringValue<Integer> value = new ExpiringValue<>(Duration.ofMinutes(1), () -> {
            reading.countDown();
            pause();
            return reads.incrementAndGet();
        });
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            results.add(pool.submit(() -> value.get(NOW)));
            assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 7; i++) {
                results.add(pool.submit(() -> value.get(NOW)));
            }
            for (Future<Integer> result : results) {
                assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(reads).hasValue(1);
    }

    private static void pause() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
