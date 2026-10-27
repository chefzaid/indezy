package dev.swirlit.indezy.integration;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Every feature count runs against the real schema and counts what two workspaces use. */
class FeatureAdoptionMetricsIntegrationTest extends TwoAccountsIntegrationTest {

    @Autowired
    private MeterRegistry registry;

    @Test
    void countsTheWorkspacesUsingEachFeature() {
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Java lead", "dailyRate", 650, "clientId", aliceClientId), Map.class);
        call(alice, HttpMethod.POST, "/seasons", Map.of("name", "Autumn", "startDate", "2026-09-01"), Map.class);

        assertThat(registry.get("indezy.accounts").gauge().value()).isGreaterThanOrEqualTo(2);
        registry.find("indezy.feature.workspaces").gauges().forEach(gauge ->
            assertThat(gauge.value()).as(gauge.getId().getTag("feature")).isNotNaN().isGreaterThanOrEqualTo(0));
        assertThat(registry.get("indezy.feature.workspaces").tag("feature", "opportunities").gauge().value()).isGreaterThanOrEqualTo(1);
    }
}
