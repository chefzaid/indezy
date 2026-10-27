package dev.swirlit.indezy.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.swirlit.indezy.dto.RegisterRequest;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** An account export brought into another workspace: previewed, imported, imported again, and refused when broken. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "indezy.security.permit-all=false")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class WorkspaceImportIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void previewsThenImportsAnExportOnceAndKeepsWhatIsAlreadyThere() throws Exception {
        Account alice = register("alice");
        Account bob = register("bob");
        long acme = id(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Acme", "city", "Paris", "isFinal", true,
            "freelanceId", alice.freelanceId()), Map.class));
        long alten = id(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Alten", "city", "Lyon", "isFinal", false,
            "freelanceId", alice.freelanceId()), Map.class));
        call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Léa", "lastName", "Martin", "email", "lea@acme.example",
            "clientId", acme, "freelanceId", alice.freelanceId()), Map.class);
        call(alice, HttpMethod.POST, "/seasons", Map.of("name", "Autumn 2026", "startDate", "2026-09-01"), Map.class);
        long project = id(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Java lead", "dailyRate", 650, "clientId", acme,
            "middlemanId", alten, "startDate", "2026-11-02", "status", "INTERVIEW"), Map.class));
        call(alice, HttpMethod.POST, "/interview-steps", Map.of("title", "Client interview", "status", "PLANNED",
            "date", "2026-10-30T10:00:00", "projectId", project, "checklist", List.of(Map.of("label", "References ready", "done", false))),
            Map.class);
        call(alice, HttpMethod.POST, "/projects/" + project + "/notes", Map.of("content", "Called Léa about the rate"), Map.class);
        byte[] export = call(alice, HttpMethod.GET, "/users/export", null, byte[].class).getBody();

        Map<String, Object> preview = upload(bob, "/users/import/preview", export, Map.class).getBody();
        assertThat(preview).containsEntry("applied", false);
        assertThat(section(preview, "CLIENTS")).containsEntry("added", 2).containsEntry("duplicates", 0);
        assertThat(section(preview, "CONTACTS")).containsEntry("added", 1);
        assertThat(section(preview, "SEASONS")).containsEntry("added", 1);
        assertThat(section(preview, "PROJECTS")).containsEntry("added", 1);
        assertThat(section(preview, "PROJECT_NOTES")).containsEntry("added", 1);
        // Both workspaces start with the same job board sources: they are already there.
        assertThat(section(preview, "SOURCES")).containsEntry("added", 0);
        assertThat((List<?>) call(bob, HttpMethod.GET, "/clients/by-freelance/" + bob.freelanceId(), null, List.class).getBody()).isEmpty();

        Map<String, Object> imported = upload(bob, "/users/import", export, Map.class).getBody();
        assertThat(imported).containsEntry("applied", true);
        assertThat(section(imported, "PROJECTS")).containsEntry("added", 1);
        List<Map<String, Object>> bobProjects = call(bob, HttpMethod.GET, "/projects/by-freelance/" + bob.freelanceId(), null,
            List.class).getBody();
        assertThat(bobProjects).singleElement().satisfies(copy -> assertThat(copy).containsEntry("role", "Java lead")
            .containsEntry("clientName", "Acme").containsEntry("middlemanName", "Alten").containsEntry("status", "INTERVIEW")
            .containsEntry("seasonName", "Autumn 2026"));
        long copy = ((Number) bobProjects.get(0).get("id")).longValue();
        assertThat(copy).isNotEqualTo(project);
        List<Map<String, Object>> steps = call(bob, HttpMethod.GET, "/interview-steps/by-project/" + copy + "/ordered", null, List.class).getBody();
        assertThat(steps).singleElement().satisfies(step -> assertThat(step).containsEntry("title", "Client interview")
            .extracting("checklist").asList().hasSize(1));
        assertThat(call(bob, HttpMethod.GET, "/projects/" + copy + "/notes", null, List.class).getBody()).singleElement()
            .satisfies(note -> assertThat((Map<String, Object>) note).containsEntry("content", "Called Léa about the rate"));
        assertThat(call(bob, HttpMethod.GET, "/contacts/by-freelance/" + bob.freelanceId(), null, List.class).getBody())
            .singleElement().satisfies(contact -> assertThat((Map<String, Object>) contact).containsEntry("clientName", "Acme"));

        // A second import finds everything there.
        Map<String, Object> again = upload(bob, "/users/import", export, Map.class).getBody();
        for (String kind : List.of("SOURCES", "CLIENTS", "CONTACTS", "SEASONS", "PROJECTS", "PROJECT_NOTES")) {
            assertThat(section(again, kind)).as(kind).containsEntry("added", 0).containsEntry("invalid", 0);
        }
        assertThat(section(again, "PROJECT_NOTES")).containsEntry("duplicates", 1);
        assertThat((List<?>) call(bob, HttpMethod.GET, "/projects/by-freelance/" + bob.freelanceId(), null, List.class).getBody())
            .hasSize(1);
        assertThat((List<?>) call(alice, HttpMethod.GET, "/projects/by-freelance/" + alice.freelanceId(), null, List.class)
            .getBody()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void leavesOutBrokenRecordsAndRefusesAFileThatIsNoExport() throws Exception {
        Account carol = register("carol");
        Map<String, Object> file = Map.of(
            "clients", List.of(Map.of("id", 1, "companyName", "Globex", "city", "Paris", "isFinal", true)),
            "contacts", List.of(Map.of("id", 2, "firstName", "Ana", "lastName", "Lopez", "clientId", 99)),
            "seasons", List.of(Map.of("id", 3, "name", "Backwards", "startDate", "2026-10-01", "endDate", "2026-09-01")),
            "projects", new ArrayList<>(List.of(Map.of("id", 4, "role", " ", "dailyRate", 500, "clientId", 1),
                // A section can be null, as a file edited by hand may say.
                with(Map.of("id", 5, "role", "Go dev", "dailyRate", 600, "clientId", 1), "steps", null),
                Map.of("id", 6, "role", "Ops", "dailyRate", 550, "clientId", 1, "steps", List.of(Map.of("title", " "))))),
            "projectNotes", List.of(Map.of("projectId", 4, "content", "Lost with its opportunity")));

        Map<String, Object> report = upload(carol, "/users/import", json.writeValueAsBytes(file), Map.class).getBody();
        assertThat(section(report, "CLIENTS")).containsEntry("added", 1);
        assertThat((List<Map<String, Object>>) section(report, "PROJECTS").get("items")).first()
            .satisfies(item -> assertThat(item).containsEntry("label", "—").containsKey("reason"));
        assertThat(section(report, "CONTACTS")).containsEntry("invalid", 1);
        assertThat(section(report, "SEASONS")).containsEntry("invalid", 1);
        assertThat(section(report, "PROJECTS")).containsEntry("added", 1).containsEntry("invalid", 2);
        assertThat((List<Map<String, Object>>) section(report, "PROJECTS").get("items")).extracting(item -> item.get("status"))
            .containsExactly("INVALID", "NEW", "INVALID");
        assertThat(section(report, "PROJECT_NOTES")).containsEntry("invalid", 1);

        assertThat(upload(carol, "/users/import/preview", "not json".getBytes(StandardCharsets.UTF_8), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(carol, "/users/import", "{\"profile\": {}}".getBytes(StandardCharsets.UTF_8), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(restTemplate.postForEntity(url("/users/import/preview"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> copy = new java.util.HashMap<>(base);
        copy.put(key, value);
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> report, String kind) {
        return ((List<Map<String, Object>>) report.get("sections")).stream().filter(section -> kind.equals(section.get("kind")))
            .findFirst().orElseThrow();
    }

    private record Account(String token, Long freelanceId) {
    }

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

    private <T> ResponseEntity<T> upload(Account account, String path, byte[] content, Class<T> type) {
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "user-data.json";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(form, headers), type);
    }

    private <T> ResponseEntity<T> call(Account account, HttpMethod method, String path, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(account.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), type);
    }

    private static long id(ResponseEntity<Map> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
