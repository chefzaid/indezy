package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.GeoPlace;
import dev.swirlit.indezy.model.SavedOfferSearch;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The offer feed's saved and suggested searches over HTTP, each workspace with its own. */
class OfferSearchIntegrationTest extends OfferIntegrationTestSupport {

    @Test
    @SuppressWarnings("unchecked")
    void eachWorkspaceKeepsItsSavedSearchesAndSeesWhatIsNewForThem() {
        ResponseEntity<Map> created = call(alice, HttpMethod.POST, "/offers/searches",
            Map.of("name", "Java", "filters", Map.of("q", "java", "view", "SAVED", "page", 2)));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("name", "Java").containsEntry("newOffers", 0);
        // The filters are kept, not the view or the page.
        assertThat((Map<String, Object>) created.getBody().get("filters")).containsEntry("q", "java").containsEntry("view", "FEED")
            .containsEntry("page", 0);
        Object id = created.getBody().get("id");
        // Opened half an hour ago; two offers came in ten minutes ago.
        SavedOfferSearch stored = savedSearches.findById(((Number) id).longValue()).orElseThrow();
        stored.setLastOpenedAt(Instant.now().minus(Duration.ofMinutes(30)));
        savedSearches.save(stored);
        Instant later = Instant.now().minus(Duration.ofMinutes(10));
        offers.save(offer("fw-3", "Développeur Java senior", "Exemple Conseil", later, 48.8566, 2.3522));
        offers.save(offer("fw-4", "Data engineer", null, later, 48.8566, 2.3522));

        ResponseEntity<List> listed = call(alice, HttpMethod.GET, "/offers/searches", null, List.class);
        assertThat(listed.getBody()).singleElement().satisfies(search -> assertThat((Map<String, Object>) search)
            .containsEntry("name", "Java").containsEntry("newOffers", 1));
        assertThat(call(alice, HttpMethod.GET, "/offers/searches/alerts", null).getBody()).containsEntry("newOffers", 1);
        assertThat(call(bob, HttpMethod.GET, "/offers/searches", null, List.class).getBody()).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/offers/searches/alerts", null).getBody()).containsEntry("newOffers", 0);
        assertThat(call(bob, HttpMethod.POST, "/offers/searches/" + id + "/open", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.POST, "/offers/searches", Map.of("name", " java ", "filters", Map.of())).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.POST, "/offers/searches", Map.of("name", "", "filters", Map.of())).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(call(alice, HttpMethod.POST, "/offers/searches/" + id + "/open", null).getBody()).containsEntry("newOffers", 0);
        assertThat(call(alice, HttpMethod.GET, "/offers/searches/alerts", null).getBody()).containsEntry("newOffers", 0);
        assertThat(call(bob, HttpMethod.DELETE, "/offers/searches/" + id, null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.DELETE, "/offers/searches/" + id, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/offers/searches", null, List.class).getBody()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void suggestsSearchesFromEachWorkspacesProfileUntilSavedOrDismissed() {
        GeoPlace place = new GeoPlace();
        place.setQueryKey("|paris");
        place.setFound(true);
        place.setCity("Paris");
        place.setDepartmentCode("75");
        place.setRegionCode("11");
        place.setLatitude(48.8589);
        place.setLongitude(2.347);
        place.setResolvedAt(Instant.now());
        places.save(place);
        users.findByEmail(alice.email()).ifPresent(user -> {
            user.setSkills(List.of("Java"));
            user.setPosition("Développeur Java");
            user.setTargetDailyRate(600);
            user.setCity("Paris");
            users.save(user);
        });
        paris.setSearchText("developpeur java spring exemple conseil");
        offers.save(paris);

        Map<String, Object> found = call(alice, HttpMethod.GET, "/offers/searches/suggestions", null).getBody();

        // The job title near Paris, Java near Paris, Java hybrid within 80 km: Java remote and in Île-de-France find nothing.
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) found.get("suggestions");
        assertThat(suggestions).extracting(suggestion -> ((Map<String, Object>) suggestion.get("filters")).get("q"))
            .containsExactly("Développeur Java", null, null);
        assertThat(suggestions).extracting(suggestion -> ((Map<String, Object>) suggestion.get("filters")).get("radiusKm"))
            .containsExactly(30, 30, 80);
        assertThat(suggestions.get(0)).containsEntry("place", "Paris").containsEntry("offers", 1).containsEntry("goodMatches", 1)
            .containsEntry("newThisWeek", 1);
        assertThat((Map<String, Object>) suggestions.get(0).get("filters")).containsEntry("rateMin", 600);
        assertThat((List<?>) found.get("missing")).isEmpty();

        ResponseEntity<Map> saved = call(alice, HttpMethod.POST, "/offers/searches",
            Map.of("name", "Développeur Java", "filters", suggestions.get(0).get("filters")));
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        for (int time = 0; time < 2; time++) {
            assertThat(call(alice, HttpMethod.POST, "/offers/searches/suggestions/dismissals", suggestions.get(2).get("filters"))
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
        List<Map<String, Object>> left = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/offers/searches/suggestions", null)
            .getBody().get("suggestions");
        assertThat(left).extracting(suggestion -> suggestion.get("key")).containsExactly(suggestions.get(1).get("key"));
        assertThat(dismissals.findAll()).hasSize(1);
        assertThat(call(alice, HttpMethod.POST, "/offers/searches/suggestions/dismissals", Map.of("rateMin", -1)).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<byte[]> export = call(alice, HttpMethod.GET, "/users/export", null, byte[].class);
        assertThat(new String(export.getBody(), StandardCharsets.UTF_8)).contains("\"dismissedOfferSearchSuggestions\"");

        // Bob's profile names nothing: no suggestion, and what to fill in.
        Map<String, Object> none = call(bob, HttpMethod.GET, "/offers/searches/suggestions", null).getBody();
        assertThat((List<?>) none.get("suggestions")).isEmpty();
        assertThat((List<Object>) none.get("missing")).containsExactly("SKILLS", "POSITION", "CITY", "TARGET_RATE");
        places.deleteById("|paris");
    }
}
