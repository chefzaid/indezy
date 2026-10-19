package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.model.GeoPlace;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.GeoPlaceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
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
