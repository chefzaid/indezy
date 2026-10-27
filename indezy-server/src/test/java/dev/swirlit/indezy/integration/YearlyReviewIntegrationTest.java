package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.RegisterRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A freelancer's year in review, as figures and as a PDF, for their own workspace only. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "indezy.security.permit-all=false")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class YearlyReviewIntegrationTest {

    private static final int YEAR = LocalDate.now(ZoneId.of("Europe/Paris")).getYear();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void summarizesTheYearAndExportsItAsAPdfForItsOwnerOnly() throws IOException {
        Account alice = register("Alice");
        Account bob = register("Bob");
        long client = idOf(call(alice, HttpMethod.POST, "/clients",
            Map.of("companyName", "Acme", "city", "Paris", "isFinal", true, "freelanceId", alice.freelanceId()), Map.class));
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Java Lead", "status", "WON", "dailyRate", 600,
            "clientId", client, "techStack", "Java, Kafka", "startDate", LocalDate.of(YEAR, 1, 1).toString(),
            "durationInMonths", 12, "daysPerYear", 200), Map.class);
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Go Dev", "status", "LOST", "lostReason", "RATE_TOO_LOW",
            "dailyRate", 700, "clientId", client, "techStack", "Go, Kafka"), Map.class);

        ResponseEntity<Map> review = call(alice, HttpMethod.GET, review(alice) + "?year=" + YEAR, null, Map.class);
        assertThat(review.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = review.getBody();
        assertThat(body).containsEntry("year", YEAR).containsEntry("name", "Alice Test");
        List<Map<String, Object>> missions = (List<Map<String, Object>>) body.get("missions");
        assertThat(missions).singleElement().satisfies(mission -> assertThat(mission)
            .containsEntry("role", "Java Lead").containsEntry("clientName", "Acme")
            .containsEntry("days", 200.0).containsEntry("reported", false).containsEntry("revenue", 120_000));
        assertThat((Map<String, Object>) body.get("totals")).containsEntry("revenue", 120_000).containsEntry("averageDailyRate", 600);
        assertThat((Map<String, Object>) body.get("pipeline")).containsEntry("opened", 2).containsEntry("won", 1)
            .containsEntry("lost", 1).containsEntry("conversionRate", 50);
        assertThat((List<Map<String, Object>>) body.get("topSkills")).first()
            .satisfies(skill -> assertThat(skill).containsEntry("skill", "Kafka").containsEntry("count", 2));
        assertThat((Map<String, Object>) body.get("network")).containsEntry("clients", 1).containsEntry("esns", 0);

        ResponseEntity<byte[]> pdf = call(alice, HttpMethod.GET, review(alice) + "/pdf?year=" + YEAR + "&language=EN", null,
            byte[].class);
        assertThat(pdf.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(pdf.getHeaders().getContentDisposition().getFilename()).isEqualTo("indezy-year-in-review-" + YEAR + ".pdf");
        try (PDDocument document = Loader.loadPDF(pdf.getBody())) {
            assertThat(new PDFTextStripper().getText(document).replaceAll("[ \\t]+", " "))
                .contains(YEAR + " in review", "Alice Test", "Java Lead", "Acme", "Kafka");
        }
        assertThat(call(alice, HttpMethod.GET, review(alice) + "/pdf?year=" + YEAR, null, byte[].class)
            .getHeaders().getContentDisposition().getFilename()).isEqualTo("indezy-bilan-" + YEAR + ".pdf");

        assertThat(call(alice, HttpMethod.GET, review(alice) + "?year=1999", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(bob, HttpMethod.GET, review(alice) + "?year=" + YEAR, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(bob, HttpMethod.GET, review(alice) + "/pdf?year=" + YEAR, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat((List<?>) call(bob, HttpMethod.GET, review(bob) + "?year=" + YEAR, null, Map.class).getBody().get("missions"))
            .isEmpty();
    }

    private record Account(String token, Long freelanceId) {
    }

    private static String review(Account account) {
        return "/projects/review/" + account.freelanceId();
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
        return new Account((String) response.getBody().get("token"), ((Number) user.get("id")).longValue());
    }

    private <T> ResponseEntity<T> call(Account account, HttpMethod method, String path, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), type);
    }

    private static long idOf(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
