package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The member directory over HTTP: members found by a skill, a city, an availability or remote work, on what their cards
 * show only, never the viewer, nor a member either one blocks.
 */
class NetworkDirectoryIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void closeTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, false);
    }

    private String join(Account account, String city, List<String> skills, Map<String, Object> fields, Map<String, Object> availability) {
        Map<String, Object> profile = call(account, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.put("city", city);
        profile.put("skills", skills);
        call(account, HttpMethod.PUT, "/users/profile", profile, Map.class);
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", fields, "availability", availability), Map.class);
        return handle;
    }

    @SuppressWarnings("unchecked")
    private List<String> found(Account viewer, String query) {
        Map<String, Object> directory = call(viewer, HttpMethod.GET, "/network/members?" + query, null, Map.class).getBody();
        return ((List<Map<String, Object>>) directory.get("members")).stream().map(card -> (String) card.get("handle")).toList();
    }

    @Test
    void findsMembersByWhatTheirCardsShow() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        assertThat(call(alice, HttpMethod.GET, "/network/members", null, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String skill = "Skill" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String viewer = join(alice, "Lyon", List.of(skill), ALL_SHOWN, Map.of("mode", "AUTO"));
        Map<String, Object> remote = new HashMap<>(ALL_SHOWN);
        remote.put("remote", true);
        String lyon = join(bob, "Lyon", List.of(skill, "Kafka"), remote, Map.of("mode", "LISTENING"));
        Account carolAccount = register("carol");
        String saintEtienne = join(carolAccount, "Saint-Étienne", List.of(skill), ALL_SHOWN, Map.of("mode", "AVAILABLE"));
        Map<String, Object> hidden = new HashMap<>(ALL_SHOWN);
        hidden.putAll(Map.of("stack", false, "location", false));
        String hiding = join(register("dan"), "Lyon", List.of(skill), hidden, Map.of("mode", "AVAILABLE"));

        // The skill a card hides is never found, nor the viewer.
        assertThat(found(alice, "skill=" + skill.toLowerCase())).containsExactlyInAnyOrder(lyon, saintEtienne).doesNotContain(viewer, hiding);
        assertThat(found(alice, "skill=" + skill + "&city=etienne")).containsExactly(saintEtienne);
        assertThat(found(alice, "skill=" + skill + "&availability=LISTENING")).containsExactly(lyon);
        assertThat(found(alice, "skill=" + skill + "&remote=true")).containsExactly(lyon);
        assertThat(found(alice, "city=lyon&availability=AVAILABLE")).doesNotContain(hiding, lyon);
        assertThat(call(alice, HttpMethod.GET, "/network/members?skill=" + skill + "&page=1", null, Map.class).getBody())
            .containsEntry("total", 2).containsEntry("page", 1).containsEntry("size", 20);

        // A member either one blocks leaves the directory both ways.
        call(carolAccount, HttpMethod.PUT, "/network/members/" + viewer + "/block", null, Map.class);
        assertThat(found(alice, "skill=" + skill)).containsExactly(lyon);
        assertThat(found(carolAccount, "skill=" + skill)).containsExactly(lyon);
    }
}
