package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import dev.swirlit.indezy.service.account.AccountPurge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A conversation stays for the other side when one member's account is purged, and goes with the last one. */
class ConversationPurgeIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @Autowired
    private AccountPurge purge;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    private long userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private int count(String sql, Object conversation) {
        return jdbcTemplate.queryForObject(sql, Integer.class, ((Number) conversation).longValue());
    }

    @Test
    void deletesAConversationWithItsLastMember() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/follow", null, Map.class);
        Object conversation = call(alice, HttpMethod.POST, "/network/conversations", Map.of("handle", bobHandle), Map.class).getBody().get("id");
        Object first = call(alice, HttpMethod.POST, "/network/conversations/" + conversation + "/messages", Map.of("text", "Bonjour Bob"),
            Map.class).getBody().get("id");
        call(bob, HttpMethod.PUT, "/network/messages/" + first + "/reaction?reaction=BRAVO", null, Map.class);
        call(bob, HttpMethod.POST, "/network/conversations/" + conversation + "/messages", Map.of("text", "Bonjour Alice"), Map.class);
        String messages = "SELECT count(*) FROM network_messages WHERE conversation_id = ?";
        String conversations = "SELECT count(*) FROM network_conversations WHERE id = ?";
        assertThat(count(messages, conversation)).isEqualTo(2);
        String reactions = "SELECT count(*) FROM network_message_reactions WHERE message_id IN " +
            "(SELECT id FROM network_messages WHERE conversation_id = ?)";
        assertThat(count(reactions, conversation)).isEqualTo(1);

        // Alice's account goes: Bob keeps the conversation, her messages included.
        purge.purge(userId(aliceEmail));
        assertThat(count(conversations, conversation)).isEqualTo(1);
        assertThat(count(messages, conversation)).isEqualTo(2);

        // Bob's goes too: nobody is left to read it, so it goes with its messages.
        purge.purge(userId(bobEmail));
        assertThat(count(conversations, conversation)).isZero();
        assertThat(count(messages, conversation)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM network_message_reactions WHERE message_id = ?", Integer.class,
            ((Number) first).longValue())).isZero();
    }
}
