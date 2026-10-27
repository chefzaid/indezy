package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.GeoPlace;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.OfferRateBenchmarkRepository;
import dev.swirlit.indezy.repository.OfferRateHistoryRepository;
import dev.swirlit.indezy.scraping.OfferStore;
import dev.swirlit.indezy.service.OfferRateBenchmarkService;
import dev.swirlit.indezy.service.OfferSuspicionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The offer feed over HTTP with real accounts: each workspace saves, hides and imports offers on its own,
 * an imported offer lands in the pipeline with the job board as its source, and distances are measured
 * from the profile's city.
 */
class OfferFeedIntegrationTest extends OfferIntegrationTestSupport {

    @Autowired
    private OfferRateBenchmarkService benchmarkService;

    @Autowired
    private OfferRateBenchmarkRepository benchmarks;

    @Autowired
    private OfferRateHistoryRepository nights;

    @Autowired
    private OfferSuspicionService suspicionService;

    @Autowired
    private OfferStore store;

    @Test
    void eachWorkspaceSavesAndHidesItsOwnOffers() {
        assertThat(titles(call(alice, HttpMethod.GET, "/offers", null))).containsExactly("Développeur Java Spring", "Architecte cloud");

        ResponseEntity<Map> saved = call(alice, HttpMethod.PUT, "/offers/" + paris.getId() + "/state", Map.of("saved", true, "seen", true));
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody()).containsEntry("saved", true).containsEntry("seen", true);
        call(alice, HttpMethod.PUT, "/offers/" + lyon.getId() + "/state", Map.of("hidden", true));

        assertThat(titles(call(alice, HttpMethod.GET, "/offers?view=SAVED", null))).containsExactly("Développeur Java Spring");
        assertThat(titles(call(alice, HttpMethod.GET, "/offers", null))).containsExactly("Développeur Java Spring");
        assertThat(titles(call(alice, HttpMethod.GET, "/offers?unseenOnly=true", null))).isEmpty();
        assertThat(titles(call(bob, HttpMethod.GET, "/offers", null))).containsExactly("Développeur Java Spring", "Architecte cloud");
        assertThat(titles(call(bob, HttpMethod.GET, "/offers?view=SAVED", null))).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/offers?freelanceId=" + alice.freelanceId(), null).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.getForEntity(url("/offers"), Map.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anImportedOfferJoinsThePipelineWithItsSiteAsSourceOnce() {
        ResponseEntity<Map> imported = call(alice, HttpMethod.POST, "/offers/" + paris.getId() + "/import", null);

        assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> project = imported.getBody();
        assertThat(project).containsEntry("role", "Développeur Java Spring").containsEntry("status", "CONTACT")
            .containsEntry("dailyRate", 650).containsEntry("clientName", "Exemple Conseil").containsEntry("sourceName", "Free-Work")
            .containsEntry("link", "https://www.free-work.com/fr/tech-it/job-mission/java/fw-1");
        long projectId = ((Number) project.get("id")).longValue();
        ResponseEntity<Map> again = call(alice, HttpMethod.POST, "/offers/" + paris.getId() + "/import", null);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.GET, "/offers/" + paris.getId(), null).getBody())
            .containsEntry("importedProjectId", (int) projectId).containsEntry("description", "Refonte d'un SI\nJava 21");
        assertThat(call(bob, HttpMethod.GET, "/offers/" + paris.getId(), null).getBody()).doesNotContainKey("importedProjectId");

        ResponseEntity<Map> second = call(alice, HttpMethod.POST, "/offers/" + lyon.getId() + "/import", null);
        assertThat(second.getBody()).containsEntry("clientName", "Free-Work").containsEntry("sourceName", "Free-Work");
        // The workspace was given every job board as a source when it was created: the import uses the Free-Work one.
        List<?> sources = call(alice, HttpMethod.GET, "/sources/by-freelance/" + alice.freelanceId(), null, List.class).getBody();
        assertThat(sources).hasSize(OfferSite.values().length + OfferLeftOutSite.values().length);
        assertThat(sources.stream().map(source -> ((Map<?, ?>) source).get("name")).filter("Free-Work"::equals)).hasSize(1);

        assertThat(call(alice, HttpMethod.DELETE, "/projects/" + projectId, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/offers/" + paris.getId(), null).getBody()).doesNotContainKey("importedProjectId");
        assertThat(call(alice, HttpMethod.POST, "/offers/" + paris.getId() + "/import", null).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<byte[]> export = call(alice, HttpMethod.GET, "/users/export", null, byte[].class);
        assertThat(new String(export.getBody(), StandardCharsets.UTF_8)).contains("\"jobOffers\"").contains("Développeur Java Spring");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anOfferLinkDraftsTheOpportunityAddedOnceAsCorrectedForItsWorkspaceOnly() {
        Map<String, String> link = Map.of("url", "www.free-work.com/fr/tech-it/job-mission/java/fw-1/?utm_source=alerte#postuler");
        ResponseEntity<Map> found = call(alice, HttpMethod.POST, "/offers/link", link);

        assertThat(found.getBody()).containsEntry("status", "FOUND").containsEntry("siteName", "Free-Work").doesNotContainKey("projectId");
        assertThat((Map<String, Object>) found.getBody().get("offer")).containsEntry("id", paris.getId().intValue());
        Map<String, Object> draft = (Map<String, Object>) found.getBody().get("draft");
        assertThat(draft).containsEntry("clientName", "Exemple Conseil").containsEntry("sourceName", "Free-Work");
        Map<String, Object> project = new HashMap<>((Map<String, Object>) draft.get("project"));
        assertThat(project).containsEntry("role", "Développeur Java Spring").containsEntry("dailyRate", 650);
        // Nothing is created before the form is saved: the publisher is no client yet, the seeded Free-Work source is named.
        assertThat(project.get("clientId")).isNull();
        assertThat(project.get("sourceId")).isNotNull();
        assertThat(call(alice, HttpMethod.GET, "/clients/by-freelance/" + alice.freelanceId(), null, List.class).getBody()).isEmpty();

        project.put("role", "Tech lead Java");
        project.put("status", "INTERVIEW");
        ResponseEntity<Map> created = call(alice, HttpMethod.POST, "/offers/" + paris.getId() + "/import", project);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("role", "Tech lead Java").containsEntry("status", "INTERVIEW")
            .containsEntry("clientName", "Exemple Conseil").containsEntry("sourceName", "Free-Work");
        long projectId = ((Number) created.getBody().get("id")).longValue();
        long clientId = ((Number) created.getBody().get("clientId")).longValue();

        assertThat(call(alice, HttpMethod.POST, "/offers/link", link).getBody())
            .containsEntry("status", "IN_PIPELINE").containsEntry("projectId", (int) projectId).doesNotContainKey("draft");
        assertThat(call(bob, HttpMethod.POST, "/offers/link", link).getBody()).containsEntry("status", "FOUND");
        // Bob cannot name Alice's client.
        project.put("clientId", clientId);
        assertThat(call(bob, HttpMethod.POST, "/offers/" + paris.getId() + "/import", project).getStatusCode().is4xxClientError()).isTrue();

        // Another site's page is read for its posting (see PostingImportIntegrationTest); this one cannot be read.
        ResponseEntity<Map> elsewhere = call(alice, HttpMethod.POST, "/offers/link", Map.of("url", "https://www.example.com/offre/1"));
        assertThat(elsewhere.getBody()).containsEntry("status", "SITE_UNAVAILABLE").containsEntry("siteName", "example.com");
        // Collection is off in tests, so an offer not collected yet is not read.
        assertThat(call(alice, HttpMethod.POST, "/offers/link", Map.of("url", "https://www.free-work.com/fr/mission/inconnue")).getBody())
            .containsEntry("status", "NOT_COLLECTED");
        assertThat(call(alice, HttpMethod.POST, "/offers/link", Map.of("url", "ftp://www.free-work.com/x")).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anApplicationPutsTheMissionInThePipelineWithItsStepsForItsWorkspaceOnly() {
        String today = LocalDate.now(ZoneId.of("Europe/Paris")).toString();
        ResponseEntity<Map> applied = call(alice, HttpMethod.POST, "/offers/" + paris.getId() + "/application",
            Map.of("appliedOn", today, "via", "EMAIL", "followUpInDays", 7, "language", "FR"));

        assertThat(applied.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(applied.getBody()).containsEntry("seen", true).containsEntry("application", Map.of("appliedOn", today, "via", "EMAIL"));
        long projectId = ((Number) applied.getBody().get("importedProjectId")).longValue();
        List<Map<String, Object>> steps = call(alice, HttpMethod.GET, "/interview-steps/by-project/" + projectId + "/ordered", null,
            List.class).getBody();
        assertThat(steps).extracting(step -> step.get("title")).containsExactly("Candidature envoyée (e-mail)", "Relancer la candidature");
        assertThat(steps).extracting(step -> step.get("status")).containsExactly("WAITING_FEEDBACK", "PLANNED");
        assertThat(call(alice, HttpMethod.POST, "/offers/" + paris.getId() + "/application", Map.of("appliedOn", today, "via", "PHONE"))
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.POST, "/offers/" + lyon.getId() + "/application",
            Map.of("appliedOn", LocalDate.now(ZoneId.of("Europe/Paris")).plusDays(2).toString(), "via", "PHONE")).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(titles(call(alice, HttpMethod.GET, "/offers?view=APPLIED", null))).containsExactly("Développeur Java Spring");
        assertThat(titles(call(bob, HttpMethod.GET, "/offers?view=APPLIED", null))).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/offers/" + paris.getId(), null).getBody()).doesNotContainKey("application");

        ResponseEntity<Map> withdrawn = call(alice, HttpMethod.DELETE, "/offers/" + paris.getId() + "/application", null);
        assertThat(withdrawn.getBody()).doesNotContainKey("application").containsEntry("importedProjectId", (int) projectId);
        assertThat(titles(call(alice, HttpMethod.GET, "/offers?view=APPLIED", null))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void eachWorkspaceActsOnSeveralOffersAtOnce() {
        List<Map<String, Object>> hidden = call(alice, HttpMethod.PUT, "/offers/states",
            Map.of("offerIds", List.of(paris.getId(), lyon.getId(), Long.MAX_VALUE), "hidden", true), List.class).getBody();

        assertThat(hidden).extracting(state -> state.get("hidden")).containsExactly(true, true);
        assertThat(titles(call(alice, HttpMethod.GET, "/offers", null))).isEmpty();
        assertThat(titles(call(bob, HttpMethod.GET, "/offers", null))).hasSize(2);

        Map<String, Object> imported = call(alice, HttpMethod.POST, "/offers/import", Map.of("offerIds", List.of(paris.getId(), lyon.getId())))
            .getBody();
        assertThat((List<?>) imported.get("imported")).hasSize(2);
        Map<String, Object> again = call(alice, HttpMethod.POST, "/offers/import", Map.of("offerIds", List.of(paris.getId()))).getBody();
        assertThat((List<?>) again.get("imported")).isEmpty();
        assertThat((List<?>) again.get("alreadyInPipeline")).hasSize(1);
        assertThat(call(alice, HttpMethod.GET, "/offers/" + lyon.getId(), null).getBody()).containsKey("importedProjectId");
        assertThat(call(bob, HttpMethod.GET, "/offers/" + lyon.getId(), null).getBody()).doesNotContainKey("importedProjectId");
    }

    @Test
    void measuresDistancesFromTheProfilesCity() {
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
            user.setCity("Paris");
            users.save(user);
        });

        ResponseEntity<Map> near = call(alice, HttpMethod.GET, "/offers?radiusKm=30&sort=DISTANCE", null);

        assertThat(near.getBody()).containsEntry("distanceAvailable", true);
        assertThat(titles(near)).containsExactly("Développeur Java Spring");
        assertThat(((Map<?, ?>) ((List<?>) near.getBody().get("content")).get(0)).get("distanceKm")).isEqualTo(0);
        assertThat(call(bob, HttpMethod.GET, "/offers?radiusKm=30", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        places.deleteById("|paris");
    }

    @Test
    void matchesASkillWhateverSpellingTheOfferUses() {
        lyon.setSearchText("architecte cloud kubernetes");
        offers.save(lyon);
        users.findByEmail(alice.email()).ifPresent(user -> {
            user.setSkills(List.of("K8s", "Kube"));
            users.save(user);
        });

        ResponseEntity<Map> best = call(alice, HttpMethod.GET, "/offers?sort=MATCH", null);

        assertThat(titles(best)).containsExactly("Architecte cloud", "Développeur Java Spring");
        Map<?, ?> match = (Map<?, ?>) ((Map<?, ?>) ((List<?>) best.getBody().get("content")).get(0)).get("match");
        // Two spellings of one skill count once: Kubernetes is the profile's single skill, and the offer names it.
        assertThat(match.get("skills")).isEqualTo(List.of("K8s"));
        assertThat(match.get("score")).isEqualTo(100);
        assertThat(titles(call(alice, HttpMethod.GET, "/offers?matchesProfile=true", null))).containsExactly("Architecte cloud");
    }

    @Test
    void scoresTheOffersAgainstEachWorkspacesProfile() {
        users.findByEmail(alice.email()).ifPresent(user -> {
            user.setSkills(List.of("Spring", "Go"));
            user.setTargetDailyRate(600);
            users.save(user);
        });

        ResponseEntity<Map> best = call(alice, HttpMethod.GET, "/offers?sort=MATCH", null);

        assertThat(best.getBody()).containsEntry("matchAvailable", true);
        assertThat(titles(best)).containsExactly("Développeur Java Spring", "Architecte cloud");
        // Spring of two skills (30 of 60) and the rate met (25), over 85 points: no city to measure from.
        Map<?, ?> match = (Map<?, ?>) ((Map<?, ?>) ((List<?>) best.getBody().get("content")).get(0)).get("match");
        assertThat(match.get("score")).isEqualTo(65);
        assertThat(match.get("skills")).isEqualTo(List.of("Spring"));
        assertThat(match.get("rate")).isEqualTo("MEETS");
        assertThat(titles(call(alice, HttpMethod.GET, "/offers?matchesProfile=true", null))).containsExactly("Développeur Java Spring");
        ResponseEntity<Map> unscored = call(bob, HttpMethod.GET, "/offers?matchesProfile=true&sort=MATCH", null);
        assertThat(unscored.getBody()).containsEntry("matchAvailable", false);
        assertThat(titles(unscored)).containsExactly("Développeur Java Spring", "Architecte cloud");
        assertThat(((Map<?, ?>) ((List<?>) unscored.getBody().get("content")).get(0)).containsKey("match")).isFalse();

        // Alice's availability, set on her profile, is her workspace's: the offers, which state no start, earn 5 of 10 more.
        Map<String, Object> profile = new HashMap<>(call(alice, HttpMethod.GET, "/users/profile", null).getBody());
        String nextMonth = LocalDate.now(ZoneId.of("Europe/Paris")).plusMonths(1).toString();
        profile.put("availabilityDate", nextMonth);
        assertThat(call(alice, HttpMethod.PUT, "/users/profile", profile).getBody()).containsEntry("availabilityDate", nextMonth);
        assertThat(call(alice, HttpMethod.GET, "/users/profile", null).getBody()).containsEntry("availabilityDate", nextMonth);
        Map<?, ?> available = (Map<?, ?>) ((Map<?, ?>) ((List<?>) call(alice, HttpMethod.GET, "/offers?sort=MATCH", null).getBody()
            .get("content")).get(0)).get("match");
        assertThat(available.get("availability")).isEqualTo("UNKNOWN");
        assertThat(available.get("score")).isEqualTo(63);
    }

    @Test
    void describesTheMarketOfEachWorkspacesFeed() {
        users.findByEmail(alice.email()).ifPresent(user -> {
            user.setTargetDailyRate(650);
            users.save(user);
        });
        call(alice, HttpMethod.PUT, "/offers/" + lyon.getId() + "/state", Map.of("hidden", true));

        ResponseEntity<Map> market = call(alice, HttpMethod.GET, "/offers/market", null);

        assertThat(market.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(market.getBody()).containsEntry("offers", 1);
        Map<?, ?> rates = (Map<?, ?>) market.getBody().get("rates");
        assertThat(List.of(rates.get("median"), rates.get("target"), rates.get("meetTarget"))).containsExactly(600, 650, 100);
        ResponseEntity<Map> bobs = call(bob, HttpMethod.GET, "/offers/market", null);
        assertThat(bobs.getBody()).containsEntry("offers", 2);
        assertThat(bobs.getBody().get("skills")).isEqualTo(List.of(Map.of("value", "Java", "offers", 2)));
        assertThat(bobs.getBody().get("intermediaries")).isEqualTo(List.of(Map.of("value", "Exemple Conseil", "offers", 1)));
        assertThat(call(bob, HttpMethod.GET, "/offers/market?radiusKm=30", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void keepsTheSitesEachWorkspaceExcludesOutOfItsFeed() {
        ResponseEntity<Map> excluded = call(alice, HttpMethod.PUT, "/offers/sites/FREE_WORK/exclusion", Map.of("excluded", true));

        assertThat(excluded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(titles(call(alice, HttpMethod.GET, "/offers", null))).isEmpty();
        assertThat(call(alice, HttpMethod.GET, "/offers/market", null).getBody()).containsEntry("offers", 0);
        assertThat(titles(call(bob, HttpMethod.GET, "/offers", null))).containsExactly("Développeur Java Spring", "Architecte cloud");
        assertThat(freeWork(call(alice, HttpMethod.GET, "/offers/sites/health", null))).containsEntry("excluded", true)
            .containsEntry("openOffers", 2).containsEntry("collectedDay", 2);
        assertThat(freeWork(call(bob, HttpMethod.GET, "/offers/sites/health", null))).containsEntry("excluded", false);
        assertThat(call(alice, HttpMethod.GET, "/offers/sites/health", null).getBody()).containsEntry("refreshEnabled", false);
        // Collection is off in tests: nothing is read on request.
        assertThat(call(alice, HttpMethod.POST, "/offers/sites/FREE_WORK/refresh", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        call(alice, HttpMethod.PUT, "/offers/sites/FREE_WORK/exclusion", Map.of("excluded", false));
        assertThat(titles(call(alice, HttpMethod.GET, "/offers", null))).containsExactly("Développeur Java Spring", "Architecte cloud");
        // The development frontend runs on another origin: it reads when a board can be read again from Retry-After.
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(alice.token());
        headers.setOrigin("http://localhost:4200");
        ResponseEntity<String> crossOrigin = restTemplate.exchange(url("/offers/sites/health"), HttpMethod.GET, new HttpEntity<>(headers),
            String.class);
        assertThat(crossOrigin.getHeaders().getAccessControlExposeHeaders()).contains("Retry-After");
    }

    @Test
    void recordsEachWorkspacesVisitsOfTheSitesToVisit() {
        ResponseEntity<Map> visited = call(alice, HttpMethod.PUT, "/offers/sites/left-out/MALT/visit", null);

        assertThat(visited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(leftOut(call(alice, HttpMethod.GET, "/offers/sites/health", null), "MALT")).containsEntry("kind", "MATCHING")
            .containsEntry("access", "PROFILE").containsKey("visitedAt");
        assertThat(leftOut(call(bob, HttpMethod.GET, "/offers/sites/health", null), "MALT").get("visitedAt")).isNull();
        assertThat(call(alice, HttpMethod.PUT, "/offers/sites/left-out/LINKEDIN/visit", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        call(alice, HttpMethod.DELETE, "/offers/sites/left-out/MALT/visit", null);
        assertThat(leftOut(call(alice, HttpMethod.GET, "/offers/sites/health", null), "MALT").get("visitedAt")).isNull();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> leftOut(ResponseEntity<Map> health, String site) {
        return ((List<?>) health.getBody().get("leftOut")).stream().map(entry -> (Map<String, Object>) entry)
            .filter(entry -> site.equals(entry.get("site"))).findFirst().orElseThrow();
    }

    @Test
    void hidesThePublishersEachWorkspaceBlocks() {
        ResponseEntity<Map> blocked = call(alice, HttpMethod.POST, "/offers/blocked-publishers", Map.of("name", "EXEMPLE conseil"));

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(titles(call(alice, HttpMethod.GET, "/offers", null))).containsExactly("Architecte cloud");
        assertThat(titles(call(bob, HttpMethod.GET, "/offers", null))).containsExactly("Développeur Java Spring", "Architecte cloud");
        assertThat(call(alice, HttpMethod.GET, "/offers/blocked-publishers", null, List.class).getBody())
            .isEqualTo(List.of(Map.of("id", blocked.getBody().get("id"), "name", "EXEMPLE conseil")));
        assertThat(call(bob, HttpMethod.DELETE, "/offers/blocked-publishers/" + blocked.getBody().get("id"), null).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        call(alice, HttpMethod.DELETE, "/offers/blocked-publishers/" + blocked.getBody().get("id"), null);
        assertThat(titles(call(alice, HttpMethod.GET, "/offers", null))).containsExactly("Développeur Java Spring", "Architecte cloud");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> freeWork(ResponseEntity<Map> health) {
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((List<?>) health.getBody().get("sites")).stream().map(site -> (Map<String, Object>) site)
            .filter(site -> "FREE_WORK".equals(site.get("site"))).findFirst().orElseThrow();
    }

    @Test
    @SuppressWarnings("unchecked")
    void comparesEachOffersRateWithTheMedianOfItsSkill() {
        Instant now = Instant.now();
        // Ten more Java missions in Île-de-France from 500 to 950 €: with the two offers, twelve in all of France.
        for (int index = 0; index < 10; index++) {
            JobOffer other = offer("fw-rate-" + index, "Développeur Java " + index, null, now.minus(Duration.ofHours(5)), 48.85, 2.35);
            other.setRateMin(500 + index * 50);
            other.setRateMax(null);
            other.setRegionCode("11");
            offers.save(other);
        }
        assertThat(benchmarkService.refresh()).isEqualTo(2);

        // Paris states 550 to 650 € (600) and no region: against the median of France, 675 €.
        Map<String, Object> market = (Map<String, Object>) call(alice, HttpMethod.GET, "/offers/" + paris.getId(), null).getBody()
            .get("rateMarket");
        assertThat(market).containsEntry("percent", -11).containsEntry("median", 675).containsEntry("offers", 12)
            .containsEntry("skill", "Java").doesNotContainKey("regionName");

        // The night joins the trends: Alice's profile names Java, and Île-de-France has its own median.
        users.findByEmail(alice.email()).ifPresent(user -> {
            user.setSkills(List.of("Java", "Kafka"));
            users.save(user);
        });
        Map<String, Object> trends = call(alice, HttpMethod.GET, "/offers/market/trends?region=11", null).getBody();
        assertThat(trends).containsEntry("skills", List.of("Java", "Kafka")).containsEntry("skill", "Java")
            .containsEntry("regionName", "Île-de-France");
        assertThat((Map<String, Object>) trends.get("latest")).containsEntry("median", 725).containsEntry("offers", 10);
        assertThat((List<Map<String, Object>>) trends.get("changes")).extracting(change -> change.get("days")).containsExactly(30, 90, 365);
        assertThat(call(alice, HttpMethod.GET, "/offers/market/trends?skill=Kafka", null).getBody()).doesNotContainKey("latest");
        assertThat(call(alice, HttpMethod.GET, "/offers/market/trends?skill=" + "x".repeat(61), null).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        benchmarks.deleteAll();
        nights.deleteAll();
    }

    @Test
    @SuppressWarnings("unchecked")
    void flagsSuspiciousOffersAndHidesThemOnRequest() {
        JobOffer anonymous = offer("fw-anonymous", "Développeur Java urgent", null, Instant.now().minus(Duration.ofHours(2)), 48.85, 2.35);
        anonymous.setDescription(null);
        offers.save(anonymous);
        assertThat(suspicionService.refresh()).isEqualTo(1);

        List<Map<String, Object>> feed = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/offers", null).getBody().get("content");
        assertThat(feed).filteredOn(item -> "Développeur Java urgent".equals(item.get("title"))).singleElement()
            .satisfies(item -> assertThat(item).containsEntry("suspicion", List.of("NO_PUBLISHER_NO_DESCRIPTION")));
        assertThat(feed).filteredOn(item -> item.containsKey("suspicion")).hasSize(1);
        assertThat(titles(call(alice, HttpMethod.GET, "/offers?hideSuspicious=true", null)))
            .containsExactly("Développeur Java Spring", "Architecte cloud");
        // Read again with nothing new, nothing changes.
        assertThat(suspicionService.refresh()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void describesAPublisherForEachWorkspace() {
        assertThat(call(alice, HttpMethod.POST, "/offers/" + paris.getId() + "/import", null).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(call(alice, HttpMethod.POST, "/offers/blocked-publishers", Map.of("name", "EXEMPLE conseil")).getStatusCode().is2xxSuccessful())
            .isTrue();

        Map<String, Object> publisher = call(alice, HttpMethod.GET, "/offers/publishers/exemple-CONSEIL", null).getBody();

        assertThat(publisher).containsEntry("name", "Exemple Conseil").containsEntry("openMissions", 1).containsEntry("medianRate", 600)
            .containsKey("blockId");
        assertThat((Map<String, Object>) publisher.get("client")).containsEntry("name", "Exemple Conseil").containsEntry("intermediary", true);
        assertThat((List<Map<String, Object>>) publisher.get("offers")).extracting(offer -> offer.get("title"))
            .containsExactly("Développeur Java Spring");
        // Bob has neither the client nor the block.
        assertThat(call(bob, HttpMethod.GET, "/offers/publishers/Exemple Conseil", null).getBody())
            .doesNotContainKeys("client", "blockId");
        assertThat(call(alice, HttpMethod.GET, "/offers/publishers/Personne", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsWhatEachOffersSiteChangedAndHowOftenItsMissionCameBack() {
        JobOffer read = offer("fw-1", "Développeur Java Spring", "Exemple Conseil", Instant.now(), 48.8566, 2.3522);
        read.setExternalId(paris.getExternalId());
        read.setRateMax(700);
        read.setPublisherKey("dev-java-spring");
        JobOffer again = offer("fw-again", "Développeur Java Spring", "Exemple Conseil", Instant.now(), 48.8566, 2.3522);
        again.setPublisherKey("dev-java-spring");
        JobOffer third = offer("fw-third", "Développeur Java Spring", "Exemple Conseil", Instant.now(), 48.8566, 2.3522);
        third.setPublisherKey("dev-java-spring");
        store.save(OfferSite.FREE_WORK, List.of(read, again, third));

        Map<String, Object> history = (Map<String, Object>) call(alice, HttpMethod.GET, "/offers/" + paris.getId(), null).getBody()
            .get("history");
        assertThat((List<Map<String, Object>>) history.get("changes")).singleElement()
            .satisfies(change -> assertThat(change).containsEntry("field", "RATE").containsEntry("before", "550|650")
                .containsEntry("after", "550|700"));
        Map<String, Object> newer = (Map<String, Object>) call(alice, HttpMethod.GET, "/offers/" + offers.findAll().stream()
            .filter(offer -> offer.getExternalId().startsWith("fw-third")).findFirst().orElseThrow().getId(), null).getBody().get("history");
        assertThat(newer).containsEntry("publishedTimes", 2).doesNotContainKey("changes");
    }
}
