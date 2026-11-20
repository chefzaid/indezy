package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.enums.DigestSection;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import dev.swirlit.indezy.service.NetworkDigestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The network's week for the weekly digest: Alice follows Bob, whose post Carol likes; Bob passes on a mission matching
 * Alice's card; Bob and Carol follow Alice and view her page. Each section left out leaves its part out.
 */
class NetworkDigestIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @Autowired
    private NetworkDigestService digest;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String joinWith(Account account, String city, List<String> skills) {
        Map<String, Object> profile = call(account, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.put("city", city);
        profile.put("skills", skills);
        call(account, HttpMethod.PUT, "/users/profile", profile, Map.class);
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    private Long userId(Account account) {
        return ((Number) call(account, HttpMethod.GET, "/users/profile", null, Map.class).getBody().get("id")).longValue();
    }

    @Test
    void tellsTheMembersWeek() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String skill = "Skill" + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String aliceHandle = joinWith(alice, "Lyon", List.of(skill));
        String bobHandle = joinWith(bob, "Lyon", List.of("Java"));
        Account carol = register("carol");
        joinWith(carol, "Brest", List.of("Java"));
        call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/follow", null, Map.class);
        Object liked = call(bob, HttpMethod.POST, "/network/posts", Map.of("text", "Retour sur Kafka en production"), Map.class).getBody().get("id");
        call(bob, HttpMethod.POST, "/network/posts", Map.of("text", "Sans réaction"), Map.class);
        call(carol, HttpMethod.PUT, "/network/posts/" + liked + "/like", null, Map.class);
        Object mission = call(bob, HttpMethod.POST, "/network/posts", Map.of("text", "Mission à Lyon", "kind", "MISSION", "details",
            Map.of("role", "Lead " + skill, "rateMin", 600, "rateMax", 700, "city", "Lyon", "stack", List.of(skill))), Map.class).getBody().get("id");
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        call(carol, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        call(carol, HttpMethod.POST, "/network/members/" + aliceHandle + "/views", null, String.class);

        Long aliceId = userId(alice);
        NetworkDtos.NetworkWeek week = digest.week(aliceId, LocalDateTime.now().minusDays(7), EnumSet.allOf(DigestSection.class), 5);
        assertThat(week.handle()).isEqualTo(aliceHandle);
        assertThat(week.followed()).singleElement().satisfies(line -> {
            assertThat(line.postId()).isEqualTo(((Number) liked).longValue());
            assertThat(line.reactions()).isEqualTo(1);
            assertThat(line.excerpt()).isEqualTo("Retour sur Kafka en production");
        });
        assertThat(week.missions()).extracting(NetworkDtos.DigestLine::postId).containsExactly(((Number) mission).longValue());
        assertThat(week.newFollowers()).isEqualTo(2);
        assertThat(week.followerNames()).hasSize(2);
        assertThat(week.viewers()).isEqualTo(1);

        NetworkDtos.NetworkWeek none = digest.week(aliceId, LocalDateTime.now().minusDays(7), EnumSet.of(DigestSection.PIPELINE), 5);
        assertThat(none.isEmpty()).isTrue();
        assertThat(digest.week(aliceId, LocalDateTime.now().plusDays(1), EnumSet.allOf(DigestSection.class), 5).isEmpty()).isTrue();
    }
}
