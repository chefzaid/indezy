package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.config.FeatureAdoptionMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every feature count runs against the real schema and counts what two workspaces use. The counts are read in a registry
 * of the test's own: the application's are kept for an hour, so a test that read them earlier (the admin overview) would
 * leave them as they were then.
 */
class FeatureAdoptionMetricsIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    void countsTheWorkspacesUsingEachFeature() {
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Java lead", "dailyRate", 650, "clientId", aliceClientId), Map.class);
        call(alice, HttpMethod.POST, "/seasons", Map.of("name", "Autumn", "startDate", "2026-09-01"), Map.class);

        MeterRegistry registry = new SimpleMeterRegistry();
        new FeatureAdoptionMetrics(registry, jdbcTemplate);

        assertThat(registry.get("indezy.accounts").gauge().value()).isGreaterThanOrEqualTo(2);
        registry.find("indezy.feature.workspaces").gauges().forEach(gauge ->
            assertThat(gauge.value()).as(gauge.getId().getTag("feature")).isNotNaN().isGreaterThanOrEqualTo(0));
        assertThat(registry.get("indezy.feature.workspaces").tag("feature", "opportunities").gauge().value()).isGreaterThanOrEqualTo(1);
    }
}
