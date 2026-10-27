package dev.swirlit.indezy.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How many workspaces use each feature, published as {@code indezy.feature.workspaces{feature}} beside the number of
 * accounts: counts read from the database, at most once an hour when Prometheus scrapes, so they tell which features are
 * used without following anyone. Requests per route ({@code http.server.requests}) tell how often.
 */
@Component
public class FeatureAdoptionMetrics {

    static final Duration REFRESH = Duration.ofHours(1);

    /** Each feature and the query counting the workspaces (or, for account settings, the accounts) that use it. */
    static final Map<String, String> FEATURES = features();

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private volatile Map<String, Long> counts = Map.of();
    private volatile Instant countedAt = Instant.MIN;

    @Autowired
    public FeatureAdoptionMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        this(registry, jdbc, Clock.systemUTC());
    }

    FeatureAdoptionMetrics(MeterRegistry registry, JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        Gauge.builder("indezy.accounts", this, metrics -> metrics.count("accounts")).register(registry);
        FEATURES.keySet().stream().filter(feature -> !"accounts".equals(feature)).forEach(feature ->
            Gauge.builder("indezy.feature.workspaces", this, metrics -> metrics.count(feature)).tag("feature", feature).register(registry));
    }

    /** The feature's count, the counts being read again once they are older than an hour. */
    double count(String feature) {
        if (Duration.between(countedAt, clock.instant()).compareTo(REFRESH) >= 0) {
            refresh();
        }
        Long value = counts.get(feature);
        return value == null ? Double.NaN : value;
    }

    private synchronized void refresh() {
        if (Duration.between(countedAt, clock.instant()).compareTo(REFRESH) < 0) {
            return;
        }
        Map<String, Long> read = new LinkedHashMap<>();
        FEATURES.forEach((feature, query) -> read.put(feature, jdbc.queryForObject(query, Long.class)));
        counts = Map.copyOf(read);
        countedAt = clock.instant();
    }

    private static Map<String, String> features() {
        Map<String, String> features = new LinkedHashMap<>();
        features.put("accounts", "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL");
        features.put("opportunities", "SELECT COUNT(DISTINCT freelance_id) FROM projects");
        features.put("journal_notes", "SELECT COUNT(DISTINCT p.freelance_id) FROM project_notes n JOIN projects p ON p.id = n.project_id");
        features.put("attachments", "SELECT COUNT(DISTINCT p.freelance_id) FROM project_attachments a JOIN projects p ON p.id = a.project_id");
        features.put("activity_reports", "SELECT COUNT(DISTINCT p.freelance_id) FROM activity_reports r JOIN projects p ON p.id = r.project_id");
        features.put("seasons", "SELECT COUNT(DISTINCT freelance_id) FROM seasons");
        features.put("pipeline_templates", "SELECT COUNT(DISTINCT freelance_id) FROM pipeline_templates");
        features.put("email_templates", "SELECT COUNT(DISTINCT freelance_id) FROM email_templates");
        features.put("contact_exchanges", "SELECT COUNT(DISTINCT c.freelance_id) FROM contact_interactions i JOIN contacts c ON c.id = i.contact_id");
        features.put("offer_feed", "SELECT COUNT(DISTINCT freelance_id) FROM job_offer_states");
        features.put("saved_searches", "SELECT COUNT(DISTINCT freelance_id) FROM saved_offer_searches");
        features.put("public_profile", "SELECT COUNT(*) FROM users WHERE public_profile_enabled = TRUE AND deleted_at IS NULL");
        features.put("auto_archive", "SELECT COUNT(*) FROM users WHERE auto_archive = TRUE AND deleted_at IS NULL");
        features.put("two_factor", "SELECT COUNT(*) FROM users WHERE two_factor_enabled = TRUE AND deleted_at IS NULL");
        return Map.copyOf(features);
    }
}
