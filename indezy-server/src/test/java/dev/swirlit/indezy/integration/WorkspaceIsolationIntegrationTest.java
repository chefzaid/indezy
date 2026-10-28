package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
/**
 * Two real accounts (registered through the API, authenticated with their JWTs) must never see
 * or change each other's workspace.
 */
class WorkspaceIsolationIntegrationTest extends TwoAccountsIntegrationTest {

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
        assertThat(call(bob, HttpMethod.PATCH, "/projects/bulk",
            Map.of("projectIds", List.of(bobProjectId, projectId), "action", "DELETE"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/projects/" + projectId, null, Map.class).getBody().get("archivedAt")).isNull();
        assertThat(call(bob, HttpMethod.GET, "/projects/" + bobProjectId, null, Map.class).getBody().get("archivedAt")).isNull();
    }

    @Test
    void anotherAccountCannotDeleteClientsOrContactsInBulk() {
        long aliceContact = idOf(call(alice, HttpMethod.POST, "/contacts",
            Map.of("firstName", "Claire", "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));
        long bobClient = idOf(call(bob, HttpMethod.POST, "/clients",
            Map.of("companyName", "Bob Corp", "city", "Lyon", "isFinal", true, "freelanceId", bob.freelanceId()), Map.class));

        assertThat(call(bob, HttpMethod.POST, "/clients/bulk-delete", Map.of("ids", List.of(bobClient, aliceClientId)),
            String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, "/contacts/bulk-delete", Map.of("ids", List.of(aliceContact)),
            String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/clients/" + aliceClientId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(alice, HttpMethod.GET, "/contacts/" + aliceContact, null, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(bob, HttpMethod.POST, "/clients/bulk-delete", Map.of("ids", List.of(bobClient)), Map.class).getBody())
            .containsEntry("deleted", 1);
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
        ResponseEntity<Map> uploaded = exchange("/projects/" + projectId + "/attachments", HttpMethod.POST,
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
    void contactInteractionsStayInTheirWorkspace() {
        long aliceContact = idOf(call(alice, HttpMethod.POST, "/contacts",
            Map.of("firstName", "Claire", "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));
        Map<String, Object> exchange = Map.of("occurredOn", LocalDate.now().minusDays(1).toString(), "channel", "EMAIL",
            "direction", "OUTGOING", "replied", true);
        long interaction = idOf(call(alice, HttpMethod.POST, "/contacts/" + aliceContact + "/interactions", exchange, Map.class));
        long bobClient = idOf(call(bob, HttpMethod.POST, "/clients",
            Map.of("companyName", "Bob Corp", "city", "Lyon", "isFinal", true, "freelanceId", bob.freelanceId()), Map.class));
        long bobContact = idOf(call(bob, HttpMethod.POST, "/contacts",
            Map.of("firstName", "Marc", "clientId", bobClient, "freelanceId", bob.freelanceId()), Map.class));

        String alicePath = "/contacts/" + aliceContact + "/interactions";
        assertThat(call(bob, HttpMethod.GET, alicePath, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, alicePath, exchange, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Nor through his own contact.
        String throughBob = "/contacts/" + bobContact + "/interactions/" + interaction;
        assertThat(call(bob, HttpMethod.PUT, throughBob, exchange, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, throughBob, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, alicePath, null, Map.class).getBody()).containsEntry("responseRate", 100);

        // Nor tie his own exchanges to Alice's opportunities, or read the exchanges about them.
        long aliceProject = idOf(call(alice, HttpMethod.POST, "/projects",
            Map.of("role", "Dev", "dailyRate", 500, "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));
        Map<String, Object> aboutAlice = new HashMap<>(exchange);
        aboutAlice.put("projectId", aliceProject);
        assertThat(call(bob, HttpMethod.POST, "/contacts/" + bobContact + "/interactions", aboutAlice, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.GET, "/projects/" + aliceProject + "/interactions", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void contactNetworksStayInTheirWorkspace() {
        long aliceContact = idOf(call(alice, HttpMethod.POST, "/contacts",
            Map.of("firstName", "Claire", "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));
        long bobClient = idOf(call(bob, HttpMethod.POST, "/clients",
            Map.of("companyName", "Bob Corp", "city", "Lyon", "isFinal", true, "freelanceId", bob.freelanceId()), Map.class));
        long bobContact = idOf(call(bob, HttpMethod.POST, "/contacts",
            Map.of("firstName", "Marc", "clientId", bobClient, "freelanceId", bob.freelanceId()), Map.class));

        assertThat(call(bob, HttpMethod.PUT, "/contacts/" + bobContact + "/relations", Map.of("otherId", aliceContact),
            String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, "/contacts", Map.of("firstName", "Spy", "clientId", bobClient,
            "freelanceId", bob.freelanceId(), "managerId", aliceContact), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.PUT, "/contacts/" + bobContact, Map.of("firstName", "Marc", "clientId", bobClient,
            "managerId", aliceContact), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.GET, "/clients/" + aliceClientId + "/contact-relations", null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.GET, "/contacts/" + aliceContact + "/relations", null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/contacts/" + aliceContact + "/relations", null, List.class).getBody()).isEmpty();
    }

    @Test
    void theGlobalSearchOnlyReadsTheCallersWorkspace() {
        Map aliceResults = call(alice, HttpMethod.GET, "/search?q=alice", null, Map.class).getBody();
        assertThat((List<?>) aliceResults.get("clients")).hasSize(1);

        Map bobResults = call(bob, HttpMethod.GET, "/search?q=alice", null, Map.class).getBody();
        assertThat((List<?>) bobResults.get("clients")).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/search?q=alice&freelanceId=" + alice.freelanceId(), null, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }


    @Test
    void listsOnlyAnswerForTheCallersOwnWorkspace() {
        assertThat(call(bob, HttpMethod.GET, "/clients/by-freelance/" + bob.freelanceId(), null, List.class).getBody()).isEmpty();
        assertThat(call(alice, HttpMethod.GET, "/clients/by-freelance/" + alice.freelanceId(), null, List.class).getBody())
            .hasSize(1);
        for (String list : List.of("/clients", "/contacts", "/projects", "/sources", "/interview-steps")) {
            assertThat(call(bob, HttpMethod.GET, list + "/by-freelance/" + alice.freelanceId(), null, String.class).getStatusCode())
                .as(list).isEqualTo(HttpStatus.FORBIDDEN);
            // No list of every record exists to fall back on.
            assertThat(call(bob, HttpMethod.GET, list, null, String.class).getStatusCode()).as(list)
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        }
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
            .containsExactly("CONTACT", "INTERVIEW", "OFFER", "LOST", "WON");
        assertThat((List<?>) ((Map<?, ?>) board.getBody().get("columns")).get("CONTACT")).hasSize(1);

        ResponseEntity<Map> stats = call(alice, HttpMethod.GET,
            "/projects/stats/dashboard/" + alice.freelanceId() + "?seasonId=" + seasonId, null, Map.class);
        assertThat(((Number) stats.getBody().get("totalProjects")).longValue()).isEqualTo(1L);

        ResponseEntity<List> seasons = call(alice, HttpMethod.GET,
            "/seasons/by-freelance/" + alice.freelanceId(), null, List.class);
        assertThat(seasons.getBody()).hasSize(1);
        assertThat(((Map<?, ?>) seasons.getBody().get(0)).get("projectCount")).isEqualTo(1);

        // Bob can neither see Alice's season nor use it to scope his own board.
        assertThat(call(bob, HttpMethod.GET, "/seasons/by-freelance/" + alice.freelanceId(), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
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
}
