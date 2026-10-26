package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two real accounts (registered through the API, authenticated with their JWTs) must never see
 * or change each other's workspace.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "indezy.security.permit-all=false")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class WorkspaceIsolationIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Account alice;
    private Account bob;
    private Long aliceClientId;

    @Test
    void targetRate_ShouldDefaultNewSeasonsWithoutChangingExistingOnesOrOtherAccounts() {
        Map profile = call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.put("targetDailyRate", 650);
        assertThat(call(alice, HttpMethod.PUT, "/users/profile", profile, Map.class).getBody())
            .containsEntry("targetDailyRate", 650);
        assertThat(call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody())
            .containsEntry("targetDailyRate", 650);
        call(alice, HttpMethod.PUT, "/users/preferences", Map.of("theme", "dark"), Map.class);
        assertThat(call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody())
            .containsEntry("targetDailyRate", 650);
        assertThat(call(bob, HttpMethod.GET, "/users/profile", null, Map.class).getBody().get("targetDailyRate"))
            .isNull();
        assertThat(call(alice, HttpMethod.GET, "/projects/stats/dashboard/" + alice.freelanceId(), null, Map.class).getBody())
            .containsEntry("targetDailyRate", 650).containsEntry("averageObtainedDailyRate", null);
        assertThat(call(bob, HttpMethod.GET, "/projects/stats/dashboard/" + bob.freelanceId(), null, Map.class).getBody()
            .get("targetDailyRate")).isNull();

        Map season = call(alice, HttpMethod.POST, "/seasons",
            Map.of("name", "Default target", "startDate", LocalDate.now().toString(), "freelanceId", alice.freelanceId()),
            Map.class).getBody();
        assertThat(season).containsEntry("targetDailyRate", 650);
        Map override = call(alice, HttpMethod.POST, "/seasons",
            Map.of("name", "Custom target", "startDate", LocalDate.now().toString(), "freelanceId", alice.freelanceId(),
                "targetDailyRate", 700), Map.class).getBody();
        assertThat(override).containsEntry("targetDailyRate", 700);

        profile.put("targetDailyRate", null);
        assertThat(call(alice, HttpMethod.PUT, "/users/profile", profile, Map.class).getBody().get("targetDailyRate"))
            .isNull();
        assertThat(call(alice, HttpMethod.GET, "/seasons/" + season.get("id"), null, Map.class).getBody())
            .containsEntry("targetDailyRate", 650);
        Map noTarget = call(alice, HttpMethod.POST, "/seasons",
            Map.of("name", "No target", "startDate", LocalDate.now().toString(), "freelanceId", alice.freelanceId()),
            Map.class).getBody();
        assertThat(noTarget.get("targetDailyRate")).isNull();
    }

    @Test
    void targetRate_ShouldRejectNonPositiveFractionalAndOversizedValues() {
        Map profile = call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        for (Number rate : List.of(0, -1, 650.5, 2147483648L)) {
            profile.put("targetDailyRate", rate);
            assertThat(call(alice, HttpMethod.PUT, "/users/profile", profile, String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(call(alice, HttpMethod.POST, "/seasons",
                Map.of("name", "Invalid target", "startDate", LocalDate.now().toString(),
                    "freelanceId", alice.freelanceId(), "targetDailyRate", rate), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void inactivityPreference_ShouldPersistAndApplyToOnlyItsOwnWorkspace() {
        assertThat(call(alice, HttpMethod.GET, "/users/preferences", null, Map.class).getBody())
            .containsEntry("inactivityDays", 14);
        assertThat(call(alice, HttpMethod.PUT, "/users/preferences", Map.of("inactivityDays", 30), Map.class).getBody())
            .containsEntry("inactivityDays", 30);
        assertThat(call(alice, HttpMethod.GET, "/users/preferences", null, Map.class).getBody())
            .containsEntry("inactivityDays", 30);
        assertThat(call(bob, HttpMethod.GET, "/users/preferences", null, Map.class).getBody())
            .containsEntry("inactivityDays", 14);
        assertThat(call(alice, HttpMethod.GET, "/projects/kanban/" + alice.freelanceId(), null, Map.class).getBody())
            .containsEntry("inactivityDays", 30);
        assertThat(call(bob, HttpMethod.GET, "/projects/kanban/" + bob.freelanceId(), null, Map.class).getBody())
            .containsEntry("inactivityDays", 14);

        ResponseEntity<Map> project = call(alice, HttpMethod.POST, "/projects",
            Map.of("role", "Developer", "dailyRate", 600, "clientId", aliceClientId, "freelanceId", alice.freelanceId()),
            Map.class);
        assertThat(project.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long projectId = ((Number) project.getBody().get("id")).longValue();
        jdbcTemplate.update("UPDATE projects SET updated_at = ? WHERE id = ?", LocalDateTime.now().minusDays(10), projectId);
        String statsPath = "/projects/stats/dashboard/" + alice.freelanceId();
        assertThat((List<?>) call(alice, HttpMethod.GET, statsPath, null, Map.class).getBody().get("staleOpportunities"))
            .isEmpty();

        call(alice, HttpMethod.PUT, "/users/preferences", Map.of("inactivityDays", 7), Map.class);
        assertThat((List<?>) call(alice, HttpMethod.GET, statsPath, null, Map.class).getBody().get("staleOpportunities"))
            .hasSize(1);
        assertThat(call(alice, HttpMethod.PUT, "/users/preferences", Map.of("theme", "dark"), Map.class).getBody())
            .containsEntry("inactivityDays", 7);
        assertThat(call(alice, HttpMethod.GET, "/projects/kanban/" + alice.freelanceId(), null, Map.class).getBody())
            .containsEntry("inactivityDays", 7);
    }

    @Test
    void inactivityPreference_ShouldRejectInvalidValuesAndDefaultLegacyAccounts() {
        for (Number days : List.of(-1, 0, 366, 7.5)) {
            assertThat(call(alice, HttpMethod.PUT, "/users/preferences", Map.of("inactivityDays", days), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        jdbcTemplate.update("UPDATE users SET inactivity_days = NULL WHERE email = (SELECT email FROM freelances WHERE id = ?)",
            alice.freelanceId());
        assertThat(call(alice, HttpMethod.GET, "/users/preferences", null, Map.class).getBody())
            .containsEntry("inactivityDays", 14);
        assertThat(call(alice, HttpMethod.GET, "/projects/kanban/" + alice.freelanceId(), null, Map.class).getBody())
            .containsEntry("inactivityDays", 14);
    }

    private record Account(String token, Long freelanceId) {
    }

    @BeforeEach
    void registerAccountsAndAliceData() {
        alice = register("alice");
        bob = register("bob");
        ResponseEntity<Map> created = call(alice, HttpMethod.POST, "/clients",
            Map.of("companyName", "Alice Corp", "city", "Paris", "isFinal", true, "freelanceId", alice.freelanceId()),
            Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        aliceClientId = ((Number) created.getBody().get("id")).longValue();
    }

    @Test
    void anotherAccountCannotReadOrChangeTheResource() {
        assertThat(call(bob, HttpMethod.GET, "/clients/" + aliceClientId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.PUT, "/clients/" + aliceClientId,
            Map.of("companyName", "Hijacked", "city", "Paris", "isFinal", true, "freelanceId", bob.freelanceId()),
            String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, "/clients/" + aliceClientId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/clients/" + aliceClientId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
    }

    @Test
    void anotherAccountCannotAddressTheWorkspaceOrReferenceItsRecords() {
        assertThat(call(bob, HttpMethod.GET, "/projects/by-freelance/" + alice.freelanceId(), null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(bob, HttpMethod.GET, "/projects/stats/dashboard/" + alice.freelanceId(), null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Bob cannot attach a project of his own to Alice's client.
        assertThat(call(bob, HttpMethod.POST, "/projects",
            Map.of("role", "Dev", "dailyRate", 500, "clientId", aliceClientId, "freelanceId", bob.freelanceId()),
            String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Nor create records inside Alice's workspace.
        assertThat(call(bob, HttpMethod.POST, "/clients",
            Map.of("companyName", "Intruder", "city", "Lyon", "isFinal", true, "freelanceId", alice.freelanceId()),
            String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anotherAccountCannotChangeOpportunitiesOrAssignSourcesInBulk() {
        ResponseEntity<Map> project = call(alice, HttpMethod.POST, "/projects",
            Map.of("role", "Dev", "dailyRate", 500, "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class);
        long projectId = ((Number) project.getBody().get("id")).longValue();
        ResponseEntity<Map> source = call(alice, HttpMethod.POST, "/sources",
            Map.of("name", "Alice board", "type", "JOB_BOARD", "freelanceId", alice.freelanceId()), Map.class);
        long sourceId = ((Number) source.getBody().get("id")).longValue();
        ResponseEntity<Map> bobProject = call(bob, HttpMethod.POST, "/clients",
            Map.of("companyName", "Bob Corp", "city", "Lyon", "isFinal", true, "freelanceId", bob.freelanceId()), Map.class);
        long bobClientId = ((Number) bobProject.getBody().get("id")).longValue();
        long bobProjectId = ((Number) call(bob, HttpMethod.POST, "/projects",
            Map.of("role", "Ops", "dailyRate", 500, "clientId", bobClientId, "freelanceId", bob.freelanceId()), Map.class)
            .getBody().get("id")).longValue();

        assertThat(call(bob, HttpMethod.PATCH, "/projects/bulk",
            Map.of("projectIds", List.of(bobProjectId, projectId), "action", "ARCHIVE"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.PATCH, "/projects/bulk",
            Map.of("projectIds", List.of(bobProjectId), "action", "SOURCE", "sourceId", sourceId), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/projects/" + projectId, null, Map.class).getBody().get("archivedAt")).isNull();
        assertThat(call(bob, HttpMethod.GET, "/projects/" + bobProjectId, null, Map.class).getBody().get("archivedAt")).isNull();
    }

    @Test
    void pipelineTemplatesStayInTheirWorkspace() {
        long projectId = ((Number) call(alice, HttpMethod.POST, "/projects",
            Map.of("role", "Dev", "dailyRate", 500, "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class)
            .getBody().get("id")).longValue();
        call(alice, HttpMethod.POST, "/interview-steps", Map.of("title", "Call", "status", "TO_PLAN", "projectId", projectId), Map.class);
        ResponseEntity<Map> template = call(alice, HttpMethod.POST, "/pipeline-templates",
            Map.of("name", "Alice process", "projectId", projectId, "clientId", aliceClientId), Map.class);
        assertThat(template.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long templateId = ((Number) template.getBody().get("id")).longValue();

        assertThat(call(bob, HttpMethod.GET, "/pipeline-templates", null, List.class).getBody()).isEmpty();
        assertThat(call(bob, HttpMethod.POST, "/pipeline-templates/" + templateId + "/apply/" + projectId, null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, "/pipeline-templates/" + templateId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, "/pipeline-templates", Map.of("name", "Stolen", "projectId", projectId), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/pipeline-templates", null, List.class).getBody()).hasSize(1);
    }

    @Test
    void attachmentsStayInTheirWorkspace() {
        long projectId = ((Number) call(alice, HttpMethod.POST, "/projects",
            Map.of("role", "Dev", "dailyRate", 500, "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class)
            .getBody().get("id")).longValue();
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)) {
            @Override
            public String getFilename() {
                return "brief.pdf";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(alice.token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Map> uploaded = restTemplate.exchange(url("/projects/" + projectId + "/attachments"), HttpMethod.POST,
            new HttpEntity<>(form, headers), Map.class);
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long attachmentId = ((Number) uploaded.getBody().get("id")).longValue();

        assertThat(call(bob, HttpMethod.GET, "/projects/" + projectId + "/attachments", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.GET, "/projects/attachments/" + attachmentId + "/content", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, "/projects/attachments/" + attachmentId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/projects/attachments/" + attachmentId + "/content", null, byte[].class).getBody())
            .startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void emailTemplatesStayInTheirWorkspace() {
        ResponseEntity<Map> created = call(alice, HttpMethod.POST, "/email-templates",
            Map.of("name", "Relance", "subject", "Suivi", "body", "Bonjour {{firstName}}"), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long templateId = ((Number) created.getBody().get("id")).longValue();

        assertThat(call(bob, HttpMethod.GET, "/email-templates", null, List.class).getBody()).isEmpty();
        assertThat(call(bob, HttpMethod.PUT, "/email-templates/" + templateId,
            Map.of("name", "Hijacked", "subject", "x", "body", "y"), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, "/email-templates/" + templateId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.GET, "/email-templates?freelanceId=" + alice.freelanceId(), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(alice, HttpMethod.GET, "/email-templates", null, List.class).getBody()).hasSize(1);
    }

    @Test
    void listEndpointsOnlyReturnTheCallersRecords() {
        ResponseEntity<List> bobClients = call(bob, HttpMethod.GET, "/clients", null, List.class);
        assertThat(bobClients.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bobClients.getBody()).isEmpty();

        ResponseEntity<List> aliceClients = call(alice, HttpMethod.GET, "/clients", null, List.class);
        assertThat(aliceClients.getBody()).hasSize(1);

        ResponseEntity<List> bobFreelances = call(bob, HttpMethod.GET, "/freelances", null, List.class);
        assertThat(bobFreelances.getBody()).hasSize(1);
    }

    @Test
    void seasonsScopeThePipelineAndStayPrivate() {
        ResponseEntity<Map> season = call(alice, HttpMethod.POST, "/seasons",
            Map.of("name", "Autumn search", "startDate", LocalDate.now().minusDays(10).toString(),
                "targetDailyRate", 650),
            Map.class);
        assertThat(season.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(season.getBody().get("active")).isEqualTo(true);
        long seasonId = ((Number) season.getBody().get("id")).longValue();

        // A new opportunity joins the running season.
        ResponseEntity<Map> project = call(alice, HttpMethod.POST, "/projects",
            Map.of("role", "Java Lead", "dailyRate", 650, "clientId", aliceClientId), Map.class);
        assertThat(project.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Number) project.getBody().get("seasonId")).longValue()).isEqualTo(seasonId);
        assertThat(project.getBody().get("status")).isEqualTo("CONTACT");

        ResponseEntity<Map> board = call(alice, HttpMethod.GET,
            "/projects/kanban/" + alice.freelanceId() + "?seasonId=" + seasonId, null, Map.class);
        assertThat(board.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<Object>) board.getBody().get("columnOrder"))
            .containsExactly("CONTACT", "INTERVIEW", "OFFER", "WON", "LOST");
        assertThat((List<?>) ((Map<?, ?>) board.getBody().get("columns")).get("CONTACT")).hasSize(1);

        ResponseEntity<Map> stats = call(alice, HttpMethod.GET,
            "/projects/stats/dashboard/" + alice.freelanceId() + "?seasonId=" + seasonId, null, Map.class);
        assertThat(((Number) stats.getBody().get("totalProjects")).longValue()).isEqualTo(1L);

        ResponseEntity<List> seasons = call(alice, HttpMethod.GET,
            "/seasons/by-freelance/" + alice.freelanceId(), null, List.class);
        assertThat(seasons.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) seasons.getBody().get(0)).get("projectCount")).isEqualTo(1);

        // Bob can neither see Alice's season nor use it to scope his own board.
        assertThat(call(bob, HttpMethod.GET, "/seasons/" + seasonId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.GET,
            "/projects/kanban/" + bob.freelanceId() + "?seasonId=" + seasonId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, "/seasons/" + seasonId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // Deleting the season keeps its opportunities.
        assertThat(call(alice, HttpMethod.DELETE, "/seasons/" + seasonId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map> kept = call(alice, HttpMethod.GET,
            "/projects/" + project.getBody().get("id"), null, Map.class);
        assertThat(kept.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(kept.getBody().get("seasonId")).isNull();
    }

    @Test
    void deletingASourceKeepsItsOpportunitiesAndProjectsWithNotesCanBeDeleted() {
        ResponseEntity<Map> source = call(alice, HttpMethod.POST, "/sources",
            Map.of("name", "LinkedIn", "type", "SOCIAL_MEDIA", "freelanceId", alice.freelanceId()), Map.class);
        long sourceId = ((Number) source.getBody().get("id")).longValue();
        ResponseEntity<Map> project = call(alice, HttpMethod.POST, "/projects",
            Map.of("role", "Java Lead", "dailyRate", 650, "clientId", aliceClientId, "sourceId", sourceId), Map.class);
        Object projectId = project.getBody().get("id");
        assertThat(call(alice, HttpMethod.POST, "/projects/" + projectId + "/notes",
            Map.of("content", "Called the recruiter"), String.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // The source is optional: its opportunities survive, without a source.
        assertThat(call(alice, HttpMethod.DELETE, "/sources/" + sourceId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map> kept = call(alice, HttpMethod.GET, "/projects/" + projectId, null, Map.class);
        assertThat(kept.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(kept.getBody().get("sourceId")).isNull();

        // Journal notes go with their project.
        assertThat(call(alice, HttpMethod.DELETE, "/projects/" + projectId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/projects/" + projectId, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
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

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
