package dev.swirlit.indezy.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeatureAdoptionMetricsTest {

    @Test
    void readsTheCountsAtMostOnceAnHour() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-27T10:00:00Z"));
        Clock clock = new Clock() {
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
        };
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new FeatureAdoptionMetrics(registry, jdbc, clock);

        assertThat(registry.get("indezy.accounts").gauge().value()).isEqualTo(3);
        assertThat(registry.get("indezy.feature.workspaces").tag("feature", "activity_reports").gauge().value()).isEqualTo(3);
        assertThat(registry.find("indezy.feature.workspaces").gauges()).hasSize(FeatureAdoptionMetrics.FEATURES.size() - 1);
        verify(jdbc, times(FeatureAdoptionMetrics.FEATURES.size())).queryForObject(anyString(), eq(Long.class));

        now.set(now.get().plusSeconds(1800));
        registry.get("indezy.feature.workspaces").tag("feature", "seasons").gauge().value();
        verify(jdbc, times(FeatureAdoptionMetrics.FEATURES.size())).queryForObject(anyString(), eq(Long.class));

        now.set(now.get().plusSeconds(1800));
        registry.get("indezy.feature.workspaces").tag("feature", "seasons").gauge().value();
        verify(jdbc, times(2 * FeatureAdoptionMetrics.FEATURES.size())).queryForObject(anyString(), eq(Long.class));
    }
}
