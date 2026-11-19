package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.NetworkPageView;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.repository.NetworkPageViewRepository;
import dev.swirlit.indezy.service.FeatureFlagService;
import dev.swirlit.indezy.service.NetworkPageViewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **Who viewed my page** over HTTP: Bob reads that Alice opened his page (once a day however often), counts Carol browsing
 * privately without her name, never Dan who blocks him; once Bob browses privately, he sees no names; old views go.
 */
class NetworkPageViewsIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @Autowired
    private NetworkPageViewRepository views;

    @Autowired
    private NetworkPageViewService pageViews;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    private HttpStatus view(Account viewer, String handle) {
        return HttpStatus.valueOf(call(viewer, HttpMethod.POST, "/network/members/" + handle + "/views", null, String.class)
            .getStatusCode().value());
    }

    @Test
    @SuppressWarnings("unchecked")
    void tellsWhoViewedThePage() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        Account carol = register("carol");
        join(carol);
        Account dan = register("dan");
        String danHandle = join(dan);
        call(carol, HttpMethod.PUT, "/network/page-views/private?on=true", null, Map.class);

        assertThat(view(alice, bobHandle)).isEqualTo(HttpStatus.NO_CONTENT);
        view(alice, bobHandle);
        view(carol, bobHandle);
        view(dan, bobHandle);
        call(dan, HttpMethod.PUT, "/network/members/" + bobHandle + "/block", null, Map.class);
        // One's own page counts nothing; an unknown one answers as unknown.
        view(bob, bobHandle);
        assertThat(view(bob, "nobody_here")).isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> seen = call(bob, HttpMethod.GET, "/network/page-views", null, Map.class).getBody();
        assertThat(seen).containsEntry("viewers", 2).containsEntry("anonymous", 1).containsEntry("viewsThisWeek", 2)
            .containsEntry("privateViewing", false);
        List<Map<String, Object>> named = (List<Map<String, Object>>) seen.get("named");
        assertThat(named).singleElement().satisfies(viewer ->
            assertThat((Map<String, Object>) viewer.get("card")).containsEntry("handle", aliceHandle));
        assertThat(danHandle).isNotBlank();

        // Browsing privately, Bob sees no names.
        seen = call(bob, HttpMethod.PUT, "/network/page-views/private?on=true", null, Map.class).getBody();
        assertThat(seen).containsEntry("privateViewing", true).containsEntry("anonymous", 2);
        assertThat((List<Object>) seen.get("named")).isEmpty();

        // Views older than 90 days go at night.
        NetworkPageView old = views.findAll().stream().filter(found -> found.getViewerId() != null).findFirst().orElseThrow();
        old.setViewedAt(LocalDateTime.now().minusDays(100));
        views.save(old);
        long before = views.count();
        pageViews.dropOld();
        assertThat(views.count()).isEqualTo(before - 1);
    }
}
