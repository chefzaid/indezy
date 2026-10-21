package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.RegisterRequest;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.GeoPlaceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import dev.swirlit.indezy.repository.OfferBoardVisitRepository;
import dev.swirlit.indezy.repository.OfferPublisherBlockRepository;
import dev.swirlit.indezy.repository.OfferSiteExclusionRepository;
import dev.swirlit.indezy.repository.OfferSuggestionDismissalRepository;
import dev.swirlit.indezy.repository.SavedOfferSearchRepository;
import dev.swirlit.indezy.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the offer feed's HTTP tests share: two real accounts, Alice and Bob, two collected offers (one in Paris with a
 * publisher, one in Lyon without), and calls made as either account.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "indezy.security.permit-all=false")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
abstract class OfferIntegrationTestSupport {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JobOfferRepository offers;

    @Autowired
    protected JobOfferStateRepository states;

    @Autowired
    protected GeoPlaceRepository places;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected SavedOfferSearchRepository savedSearches;

    @Autowired
    protected OfferSiteExclusionRepository exclusions;

    @Autowired
    protected OfferPublisherBlockRepository blocks;

    @Autowired
    protected OfferBoardVisitRepository visits;

    @Autowired
    protected OfferSuggestionDismissalRepository dismissals;

    protected Account alice;
    protected Account bob;
    protected JobOffer paris;
    protected JobOffer lyon;

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
        blocks.deleteAll();
        visits.deleteAll();
        states.deleteAll();
        dismissals.deleteAll();
        offers.deleteAll();
    }

    protected static JobOffer offer(String externalId, String title, String company, Instant publishedAt, double latitude, double longitude) {
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

    protected static List<Object> titles(ResponseEntity<Map> page) {
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((List<?>) page.getBody().get("content")).stream().<Object>map(offer -> ((Map<?, ?>) offer).get("title")).toList();
    }

    protected Account register(String name) {
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

    protected ResponseEntity<Map> call(Account account, HttpMethod method, String path, Object body) {
        return call(account, method, path, body, Map.class);
    }

    protected <T> ResponseEntity<T> call(Account account, HttpMethod method, String path, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), type);
    }

    protected String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    protected record Account(String token, long freelanceId, String email) {
    }
}
