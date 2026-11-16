package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import dev.swirlit.indezy.service.NetworkDraftService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Drafts over HTTP: kept, changed, scheduled from a minute to 30 days ahead, published by hand or when their time comes. */
class NetworkDraftsIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @Autowired
    private NetworkDraftService drafts;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    @SuppressWarnings("unchecked")
    private List<Object> feedTexts(Account reader) {
        return ((List<Map<String, Object>>) call(reader, HttpMethod.GET, "/network/feed", null, Map.class).getBody().get("posts")).stream()
            .map(post -> post.get("text")).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsSchedulesAndPublishesDrafts() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        call(alice, HttpMethod.PUT, "/network/membership", Map.of("handle", "member_" + UUID.randomUUID().toString().replace("-", "")
            .substring(0, 8), "fields", ALL_SHOWN), Map.class);
        Instant soon = Instant.now().plus(10, ChronoUnit.MINUTES);
        long kept = idOf(call(alice, HttpMethod.POST, "/network/drafts", Map.of("text", "À relire"), Map.class));
        long scheduled = idOf(call(alice, HttpMethod.POST, "/network/drafts", Map.of("text", "Lundi 9 h", "scheduledAt", soon.toString()),
            Map.class));
        assertThat(call(alice, HttpMethod.POST, "/network/drafts", Map.of("text", "Hier", "scheduledAt", Instant.now().minusSeconds(60)
            .toString()), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.POST, "/network/drafts", Map.of("text", "Trop loin", "scheduledAt", Instant.now()
            .plus(31, ChronoUnit.DAYS).toString()), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/drafts", null, List.class).getBody())
            .extracting(draft -> ((Number) draft.get("id")).longValue()).containsExactly(scheduled, kept);
        assertThat(call(bob, HttpMethod.GET, "/network/drafts", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.PUT, "/network/drafts/" + kept, Map.of("text", "Relu"), Map.class).getBody())
            .containsEntry("text", "Relu").containsEntry("scheduledAt", null);
        assertThat(feedTexts(alice)).isEmpty();

        assertThat(call(alice, HttpMethod.POST, "/network/drafts/" + kept + "/publish", null, Map.class).getBody()).containsEntry("text", "Relu");
        assertThat(feedTexts(alice)).containsExactly("Relu");
        // The scheduled draft's time comes: the next round publishes it.
        jdbcTemplate.update("update network_drafts set scheduled_at = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(5)),
            scheduled);
        drafts.publishDue();
        assertThat(feedTexts(alice)).containsExactly("Lundi 9 h", "Relu");
        assertThat(call(alice, HttpMethod.GET, "/network/drafts", null, List.class).getBody()).isEmpty();
        long gone = idOf(call(alice, HttpMethod.POST, "/network/drafts", Map.of("text", "À jeter"), Map.class));
        assertThat(call(alice, HttpMethod.DELETE, "/network/drafts/" + gone, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.POST, "/network/drafts/" + gone + "/publish", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
