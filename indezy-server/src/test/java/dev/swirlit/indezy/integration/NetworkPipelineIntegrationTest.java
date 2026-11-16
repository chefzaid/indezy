package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Add to my pipeline over HTTP: Bob and Carol take Alice's mission; Alice sees two took it, and Bob, who let her know. */
class NetworkPipelineIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

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
    private Map<String, Object> taken(Account reader, String handle, Object postId) {
        List<Map<String, Object>> found = (List<Map<String, Object>>) call(reader, HttpMethod.GET, "/network/members/" + handle + "/posts",
            null, Map.class).getBody().get("posts");
        return (Map<String, Object>) found.stream().filter(post -> post.get("id").equals(postId)).findFirst().orElseThrow().get("taken");
    }

    @Test
    @SuppressWarnings("unchecked")
    void takesAMissionIntoThePipelineOnce() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        Account carol = register("carol");
        join(carol);
        Object mission = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Je ne peux pas la prendre", "kind", "MISSION",
            "details", Map.of("role", "Lead Java", "rateMin", 600, "rateMax", 700, "city", "Lyon", "remoteDays", 2, "start", "2027-01-04",
                "client", "Acme", "stack", List.of("Java", "Kafka"))), Map.class).getBody().get("id");

        Map<String, Object> project = call(bob, HttpMethod.POST, "/network/posts/" + mission + "/pipeline?named=true", null, Map.class)
            .getBody();
        assertThat(project).containsEntry("role", "Lead Java").containsEntry("status", "CONTACT").containsEntry("dailyRate", 700)
            .containsEntry("techStack", "Java, Kafka").containsEntry("startDate", "2027-01-04").containsEntry("remoteDaysPerMonth", 9);
        Map<String, Object> opened = call(bob, HttpMethod.GET, "/projects/" + project.get("id"), null, Map.class).getBody();
        assertThat(opened).containsEntry("clientName", "Acme").containsEntry("sourceName", "Indezy @" + aliceHandle);
        assertThat((String) opened.get("description")).contains("Je ne peux pas la prendre", "@" + aliceHandle);
        assertThat(call(bob, HttpMethod.POST, "/network/posts/" + mission + "/pipeline", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.POST, "/network/posts/" + mission + "/pipeline", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        Object plain = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Bonjour"), Map.class).getBody().get("id");
        assertThat(call(bob, HttpMethod.POST, "/network/posts/" + plain + "/pipeline", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        call(carol, HttpMethod.POST, "/network/posts/" + mission + "/pipeline", null, Map.class);
        assertThat(taken(alice, aliceHandle, mission)).containsEntry("count", 2).containsEntry("mine", false);
        assertThat(taken(bob, aliceHandle, mission)).containsEntry("count", null).containsEntry("mine", true);
        assertThat(((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/network/posts/" + mission + "/takers", null, List.class)
            .getBody())).extracting(card -> card.get("handle")).containsExactly(bobHandle);
        assertThat(call(bob, HttpMethod.GET, "/network/posts/" + mission + "/takers", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // Unnamed, the client is the workspace's confidential one; once the opportunity is deleted, the mission can be taken again.
        Object secret = call(alice, HttpMethod.POST, "/network/posts", Map.of("text", "Discrète", "kind", "MISSION",
            "details", Map.of("role", "Architecte")), Map.class).getBody().get("id");
        Map<String, Object> second = call(bob, HttpMethod.POST, "/network/posts/" + secret + "/pipeline", null, Map.class).getBody();
        assertThat(call(bob, HttpMethod.GET, "/projects/" + second.get("id"), null, Map.class).getBody())
            .containsEntry("clientName", "Client confidentiel");
        call(bob, HttpMethod.DELETE, "/projects/" + second.get("id"), null, String.class);
        assertThat(call(bob, HttpMethod.POST, "/network/posts/" + secret + "/pipeline", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
    }
}
