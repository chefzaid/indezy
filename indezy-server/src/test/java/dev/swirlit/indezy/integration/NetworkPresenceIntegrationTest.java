package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.realtime.NetworkPresence;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice sees Bob online in their conversation while he has the app open, only while both show it; once he is no longer
 * seen, he reads as offline. A group never says who is online.
 */
class NetworkPresenceIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @Autowired
    private NetworkPresence presence;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    @SuppressWarnings("unchecked")
    private Object online(Account reader) {
        List<Map<String, Object>> conversations = call(reader, HttpMethod.GET, "/network/conversations", null, List.class).getBody();
        return conversations.stream().filter(conversation -> conversation.get("group") == null).findFirst().orElseThrow().get("online");
    }

    @Test
    void showsAMemberOnlineWhileSeenAndBothShowIt() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/follow", null, Map.class);
        Object conversation = call(alice, HttpMethod.POST, "/network/conversations", Map.of("handle", bobHandle), Map.class).getBody().get("id");
        call(alice, HttpMethod.POST, "/network/conversations/" + conversation + "/messages", Map.of("text", "Bonjour Bob"), Map.class);
        assertThat(online(alice)).isEqualTo(false);

        presence.seen(bob.freelanceId());
        assertThat(online(alice)).isEqualTo(true);
        presence.seen(alice.freelanceId());
        assertThat(online(bob)).isEqualTo(true);

        // Hidden by either, neither sees the other.
        assertThat(call(bob, HttpMethod.PUT, "/network/messages/settings", Map.of("presence", false), Map.class).getBody())
            .containsEntry("presence", false);
        assertThat(online(alice)).isEqualTo(false);
        assertThat(online(bob)).isEqualTo(false);
        call(bob, HttpMethod.PUT, "/network/messages/settings", Map.of("presence", true), Map.class);
        assertThat(online(alice)).isEqualTo(true);

        // No longer seen for longer than the heartbeats allow: offline.
        jdbcTemplate.update("UPDATE network_members SET last_seen_at = ? WHERE user_id = ?",
            Timestamp.from(Instant.now().minus(NetworkPresence.ONLINE_FOR).minusSeconds(5)), bob.freelanceId());
        assertThat(online(alice)).isEqualTo(false);
        assertThat(aliceHandle).isNotBlank();
    }
}
