package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.RegisterRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole activity report flow over HTTP with real accounts: a freelance records and submits a month, the ESN
 * approves or rejects it through the public link without an account, and another workspace never reaches the reports.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "indezy.security.permit-all=false")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ActivityReportIntegrationTest {

    private static final String TOKEN_HEADER = "X-Report-Token";
    private static final String NOVEMBER = "/2026/11";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Account alice;
    private Account bob;
    private long projectId;

    @BeforeEach
    void setUp() {
        alice = register("Alice");
        bob = register("Bob");
        long clientId = id(call(alice, HttpMethod.POST, "/clients",
            Map.of("companyName", "Société Générale", "city", "Paris", "isFinal", true, "freelanceId", alice.freelanceId()),
            Map.class));
        ResponseEntity<Map> esn = call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Capgemini", "city", "Paris",
            "isFinal", false, "freelanceId", alice.freelanceId(), "activityReportTitle", "Relevé mensuel d'activité",
            "activityReportLayout", "CALENDAR_GRID", "activityReportShowsLocation", false, "activityReportLanguage", "EN"),
            Map.class);
        assertThat(esn.getBody()).containsEntry("activityReportLayout", "CALENDAR_GRID")
            .containsEntry("activityReportShowsLocation", false).containsEntry("activityReportLanguage", "EN");
        projectId = id(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Développeur Java", "dailyRate", 600,
            "status", "WON", "clientId", clientId, "middlemanId", id(esn), "startDate", "2026-09-01", "durationInMonths", 6),
            Map.class));
    }

    @Test
    void freelanceRecordsAndSubmitsAMonthThatTheEsnApprovesThroughTheLink(CapturedOutput output) throws IOException {
        Map draft = call(alice, HttpMethod.GET, reports() + NOVEMBER, null, Map.class).getBody();
        assertThat(draft).containsEntry("status", "DRAFT").containsEntry("id", null).containsEntry("businessDays", 20)
            .containsEntry("missionEnd", "2027-02-28");

        ResponseEntity<Map> saved = call(alice, HttpMethod.PUT, reports() + NOVEMBER, Map.of("comment", "Formation le 4",
            "days", List.of(day("2026-11-02", "ONSITE", false), day("2026-11-03", "REMOTE", false),
                day("2026-11-04", "ONSITE", true), day("2026-11-05", "ABSENCE", false))), Map.class);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody()).containsEntry("workedDays", 2.5).containsEntry("amount", 1500.0)
            .containsEntry("absenceDays", 1.0);
        assertThat((List<?>) call(alice, HttpMethod.GET, reports(), null, List.class).getBody()).hasSize(1);

        Map link = call(alice, HttpMethod.POST, reports() + NOVEMBER + "/submit", null, Map.class).getBody();
        String token = (String) link.get("token");
        assertThat(token).hasSize(43);
        assertThat(jdbcTemplate.queryForObject("SELECT validation_token_hash FROM activity_reports WHERE project_id = ?",
            String.class, projectId)).hasSize(64).isNotEqualTo(token);
        assertThat(call(alice, HttpMethod.PUT, reports() + NOVEMBER, Map.of("days", List.of()), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // The ESN opens the link without an account: mission and month, but no ids or money.
        ResponseEntity<Map> opened = anonymous(HttpMethod.GET, "/public/activity-reports", token, null, Map.class);
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(opened.getBody()).containsEntry("consultantName", "Alice Test").containsEntry("middlemanName", "Capgemini")
            .containsEntry("missionRole", "Développeur Java").containsEntry("showsLocation", false);
        Map publicReport = (Map) opened.getBody().get("report");
        assertThat(publicReport).containsEntry("status", "SUBMITTED").containsEntry("workedDays", 2.5)
            .containsEntry("id", null).containsEntry("dailyRate", null).containsEntry("amount", null);
        assertThat(pdfText(anonymous(HttpMethod.GET, "/public/activity-reports/pdf", token, null, byte[].class)))
            .contains("Relevé mensuel d'activité", "November 2026", "Awaiting approval", "Days worked: 2.5");

        ResponseEntity<Map> approved = anonymous(HttpMethod.POST, "/public/activity-reports/decision", token,
            Map.of("approved", true, "validatorName", "Jean Dupont", "comment", "Conforme"), Map.class);
        assertThat(((Map) approved.getBody().get("report"))).containsEntry("status", "VALIDATED")
            .containsEntry("validatorName", "Jean Dupont");
        assertThat(anonymous(HttpMethod.POST, "/public/activity-reports/decision", token,
            Map.of("approved", false, "validatorName", "Someone", "comment", "Changed my mind"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // The freelance sees the approval and gets the signed PDF.
        assertThat(call(alice, HttpMethod.GET, reports() + NOVEMBER, null, Map.class).getBody())
            .containsEntry("status", "VALIDATED").containsEntry("validatorName", "Jean Dupont");
        ResponseEntity<byte[]> pdf = call(alice, HttpMethod.GET, reports() + NOVEMBER + "/pdf", null, byte[].class);
        assertThat(pdf.getHeaders().getContentDisposition().getFilename()).isEqualTo("CRA-2026-11-developpeur-java.pdf");
        assertThat(pdfText(pdf)).contains("Approved electronically by Jean Dupont", "Comment: Conforme");

        // Reopening revokes the link.
        assertThat(call(alice, HttpMethod.POST, reports() + NOVEMBER + "/reopen", null, Map.class).getBody())
            .containsEntry("status", "DRAFT").containsEntry("validatorName", null);
        assertThat(anonymous(HttpMethod.GET, "/public/activity-reports", token, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // The token itself never reaches the logs, even with Spring MVC at DEBUG.
        assertThat(output.getAll()).doesNotContain(token);
    }

    @Test
    void esnRejectsWithAReasonAndTheFreelanceFixesAndResubmitsWithANewLink() {
        call(alice, HttpMethod.PUT, reports() + NOVEMBER, Map.of("days", List.of(day("2026-11-11", "ONSITE", false))), Map.class);
        String first = (String) call(alice, HttpMethod.POST, reports() + NOVEMBER + "/submit", null, Map.class).getBody().get("token");

        assertThat(anonymous(HttpMethod.POST, "/public/activity-reports/decision", first,
            Map.of("approved", false, "validatorName", "Jean Dupont"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        anonymous(HttpMethod.POST, "/public/activity-reports/decision", first,
            Map.of("approved", false, "validatorName", "Jean Dupont", "comment", "Le 11 est férié"), Map.class);
        assertThat(call(alice, HttpMethod.GET, reports() + NOVEMBER, null, Map.class).getBody())
            .containsEntry("status", "REJECTED").containsEntry("decisionComment", "Le 11 est férié");

        assertThat(call(alice, HttpMethod.PUT, reports() + NOVEMBER,
            Map.of("days", List.of(day("2026-11-12", "ONSITE", false))), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map resubmitted = call(alice, HttpMethod.POST, reports() + NOVEMBER + "/submit", null, Map.class).getBody();
        String second = (String) resubmitted.get("token");
        assertThat(second).isNotEqualTo(first);
        assertThat((Map) resubmitted.get("report")).containsEntry("decisionComment", null);
        assertThat(anonymous(HttpMethod.GET, "/public/activity-reports", first, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        String renewed = (String) call(alice, HttpMethod.POST, reports() + NOVEMBER + "/link", null, Map.class).getBody().get("token");
        assertThat(anonymous(HttpMethod.GET, "/public/activity-reports", second, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(anonymous(HttpMethod.GET, "/public/activity-reports", renewed, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }

    @Test
    void anotherWorkspaceAndAnonymousCallersNeverReachTheReports() {
        call(alice, HttpMethod.PUT, reports() + NOVEMBER, Map.of("days", List.of(day("2026-11-02", "ONSITE", false))), Map.class);

        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.PUT)) {
            assertThat(call(bob, method, reports() + NOVEMBER, Map.of("days", List.of()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(call(bob, HttpMethod.GET, reports(), null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, reports() + NOVEMBER + "/submit", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.GET, reports() + NOVEMBER + "/pdf", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.getForEntity(url(reports() + NOVEMBER), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous(HttpMethod.GET, "/public/activity-reports", "made-up-token", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reportsNeedAnAcceptedMissionAndLeaveWithItsDeletionWhileTheExportKeepsThem() {
        long opportunity = id(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead", "dailyRate", 700,
            "status", "INTERVIEW", "clientId", clientOf(projectId)), Map.class));
        assertThat(call(alice, HttpMethod.PUT, "/projects/" + opportunity + "/activity-reports" + NOVEMBER,
            Map.of("days", List.of()), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        long reportId = id(call(alice, HttpMethod.PUT, reports() + NOVEMBER,
            Map.of("days", List.of(day("2026-11-02", "ONSITE", false))), Map.class));
        String export = call(alice, HttpMethod.GET, "/users/export", null, String.class).getBody();
        assertThat(export).contains("\"activityReports\"", "\"2026-11-02\"", "ONSITE");

        assertThat(call(alice, HttpMethod.DELETE, "/projects/" + projectId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_reports WHERE project_id = ?", Long.class, projectId))
            .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_report_days WHERE report_id = ?", Long.class,
            reportId)).isZero();
    }

    private String reports() {
        return "/projects/" + projectId + "/activity-reports";
    }

    private long clientOf(long project) {
        return ((Number) call(alice, HttpMethod.GET, "/projects/" + project, null, Map.class).getBody().get("clientId")).longValue();
    }

    private static Map<String, Object> day(String date, String type, boolean halfDay) {
        return Map.of("date", date, "type", type, "halfDay", halfDay);
    }

    private static long id(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode().is2xxSuccessful()).as("status %s", response.getStatusCode()).isTrue();
        return ((Number) response.getBody().get("id")).longValue();
    }

    private static String pdfText(ResponseEntity<byte[]> response) throws IOException {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        try (PDDocument document = Loader.loadPDF(response.getBody())) {
            return new PDFTextStripper().getText(document).replaceAll("[ \\t]+", " ");
        }
    }

    /** Posts the DTO rather than a map, whose {@code toString} the test client would log at DEBUG with the password. */
    private Account register(String name) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName(name);
        request.setLastName("Test");
        request.setEmail(name + "." + UUID.randomUUID() + "@example.com");
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

    private <T> ResponseEntity<T> anonymous(HttpMethod method, String path, String token, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(TOKEN_HEADER, token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), type);
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }

    private record Account(String token, long freelanceId) {
    }
}
