package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.RegisterRequest;
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
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The account export carries every record its owner entered, and none of another account's. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "indezy.security.permit-all=false")
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
class UserDataExportIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void exportsTheJournalTheSeasonsAndTheFreelanceRecordOfItsOwnWorkspaceOnly() {
        Map<String, Object> registered = register("alice");
        String alice = (String) registered.get("token");
        long freelanceId = ((Number) ((Map<String, Object>) registered.get("user")).get("id")).longValue();
        String bob = (String) register("bob").get("token");
        long client = ((Number) call(alice, HttpMethod.POST, "/clients",
            Map.of("companyName", "Acme", "city", "Paris", "isFinal", true, "freelanceId", freelanceId), Map.class).getBody().get("id"))
            .longValue();
        long project = ((Number) call(alice, HttpMethod.POST, "/projects",
            Map.of("role", "Java lead", "dailyRate", 650, "clientId", client), Map.class).getBody().get("id")).longValue();
        assertThat(call(alice, HttpMethod.POST, "/projects/" + project + "/notes", Map.of("content", "Called Léa about the rate"),
            String.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(call(alice, HttpMethod.POST, "/seasons", Map.of("name", "Autumn 2026", "startDate", "2026-09-01"),
            String.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> export = exportOf(alice);
        assertThat((List<Map<String, Object>>) export.get("projectNotes")).singleElement()
            .satisfies(note -> assertThat(note).containsEntry("content", "Called Léa about the rate")
                .containsEntry("projectId", (int) project));
        assertThat((List<Map<String, Object>>) export.get("seasons")).singleElement()
            .satisfies(season -> assertThat(season).containsEntry("name", "Autumn 2026"));
        assertThat((Map<String, Object>) export.get("freelance")).containsEntry("lastName", "Test").containsKey("reversionRate");
        // The display settings nothing applied are gone from the profile.
        Map<String, Object> profile = (Map<String, Object>) export.get("profile");
        assertThat(profile).containsEntry("firstName", "alice").doesNotContainKeys("timezone", "currency");
        assertThat((Map<String, Object>) profile.get("preferences")).containsKey("theme")
            .doesNotContainKeys("dateFormat", "timeFormat", "currency", "timezone", "defaultView", "itemsPerPage", "autoSave");

        Map<String, Object> other = exportOf(bob);
        assertThat((List<?>) other.get("projectNotes")).isEmpty();
        assertThat((List<?>) other.get("seasons")).isEmpty();
        assertThat(((Number) ((Map<String, Object>) export.get("freelance")).get("id")).longValue()).isEqualTo(freelanceId);
        assertThat(((Number) ((Map<String, Object>) other.get("freelance")).get("id")).longValue()).isNotEqualTo(freelanceId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> register(String name) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName(name);
        request.setLastName("Test");
        request.setEmail(name + "." + UUID.randomUUID() + "@example.com");
        request.setPassword("Password123!");
        ResponseEntity<Map> response = restTemplate.postForEntity(url("/auth/register"), request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    /** The export comes as a file to download, so it is read as text. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> exportOf(String token) {
        return JsonMapper.builder().build().readValue(call(token, HttpMethod.GET, "/users/export", null, String.class).getBody(), Map.class);
    }

    private <T> ResponseEntity<T> call(String token, HttpMethod method, String path, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(body, headers), type);
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api" + path;
    }
}
