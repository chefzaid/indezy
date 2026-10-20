package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.model.GeoPlace;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.SavedOfferSearch;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.GeoPlaceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import dev.swirlit.indezy.repository.OfferSiteExclusionRepository;
import dev.swirlit.indezy.repository.SavedOfferSearchRepository;
import dev.swirlit.indezy.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The offer feed over HTTP with real accounts: each workspace saves, hides and imports offers on its own,
 * an imported offer lands in the pipeline with the job board as its source, and distances are measured
 * from the profile's city.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "indezy.security.permit-all=false")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class OfferFeedIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JobOfferRepository offers;

    @Autowired
    private JobOfferStateRepository states;

    @Autowired
    private GeoPlaceRepository places;

    @Autowired
    private UserRepository users;

    @Autowired
    private SavedOfferSearchRepository savedSearches;

    @Autowired
    private OfferSiteExclusionRepository exclusions;

    private Account alice;
    private Account bob;
    private JobOffer paris;
    private JobOffer lyon;

    @BeforeEach
    void setUp() {
        alice = register("Alice");
        bob = register("Bob");
        Instant now = Instant.now();
        paris = offers.save(offer("fw-1", "Développeur Java Spring", "Exemple Conseil", now.minus(Duration.ofHours(1)), 48.8566, 2.3522));
        lyon = offers.save(offer("fw-2", "Architecte cloud", null, now.minus(Duration.ofHours(3)), 45.764, 4.8357));
    }

    @AfterEach
    void tearDown() {
        savedSearches.deleteAll();
        exclusions.deleteAll();
        states.deleteAll();
        offers.deleteAll();
    }

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
        List<?> sources = call(alice, HttpMethod.GET, "/sources", null, List.class).getBody();
        assertThat(sources).hasSize(1);

        assertThat(call(alice, HttpMethod.DELETE, "/projects/" + projectId, null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/offers/" + paris.getId(), null).getBody()).doesNotContainKey("importedProjectId");
        assertThat(call(alice, HttpMethod.POST, "/offers/" + paris.getId() + "/import", null).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<byte[]> export = call(alice, HttpMethod.GET, "/users/export", null, byte[].class);
        assertThat(new String(export.getBody(), StandardCharsets.UTF_8)).contains("\"jobOffers\"").contains("Développeur Java Spring");
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> freeWork(ResponseEntity<Map> health) {
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((List<?>) health.getBody().get("sites")).stream().map(site -> (Map<String, Object>) site)
            .filter(site -> "FREE_WORK".equals(site.get("site"))).findFirst().orElseThrow();
    }

    private static JobOffer offer(String externalId, String title, String company, Instant publishedAt, double latitude, double longitude) {
        JobOffer offer = new JobOffer();
        offer.setSite(OfferSite.FREE_WORK);
        offer.setExternalId(externalId + "-" + UUID.randomUUID());
        offer.setUrl("https://www.free-work.com/fr/tech-it/job-mission/java/" + externalId);
        offer.setTitle(title);
        offer.setCompany(company);
        offer.setDescription("Refonte d'un SI\nJava 21");
        offer.setRateMin(550);
        offer.setRateMax(650);
        offer.setWorkMode(WorkMode.HYBRID);
        offer.setRemoteDaysPerWeek(2);
        offer.setLatitude(latitude);
        offer.setLongitude(longitude);
        offer.setSkills(List.of("Java"));
        offer.setSearchText(title.toLowerCase());
        offer.setPublishedAt(publishedAt);
        offer.setDatePrecision(OfferDatePrecision.EXACT);
        offer.setFirstSeenAt(publishedAt);
        offer.setLastSeenAt(publishedAt);
        return offer;
    }

    private static List<Object> titles(ResponseEntity<Map> page) {
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((List<?>) page.getBody().get("content")).stream().<Object>map(offer -> ((Map<?, ?>) offer).get("title")).toList();
    }

    private Account register(String name) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName(name);
        request.setLastName("Test");
        request.setEmail(name.toLowerCase() + "." + UUID.randomUUID() + "@example.com");
        request.setPassword("Password123!");
        ResponseEntity<Map> response = restTemplate.postForEntity(url("/auth/register"), request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> user = (Map<?, ?>) response.getBody().get("user");
        return new Account((String) response.getBody().get("token"), ((Number) user.get("id")).longValue(), request.getEmail());
    }

    private ResponseEntity<Map> call(Account account, HttpMethod method, String path, Object body) {
        return call(account, method, path, body, Map.class);
    }

    private <T> ResponseEntity<T> call(Account account, HttpMethod method, String path, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), type);
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    private record Account(String token, long freelanceId, String email) {
    }
}
