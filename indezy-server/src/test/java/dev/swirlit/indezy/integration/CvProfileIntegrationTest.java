package dev.swirlit.indezy.integration;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice keeps CV profiles, draws each as its dossier and makes one the default; Bob reaches none of hers. */
class CvProfileIntegrationTest extends TwoAccountsIntegrationTest {

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void keepsNamedProfilesDrawnFromTheProfilesFacts() throws IOException {
        call(alice, HttpMethod.PUT, "/users/profile", Map.of("firstName", "Alice", "lastName", "Martin", "email", aliceEmail, "position", "Développeuse",
            "bio", "Dix ans de Java.", "skills", List.of("Java", "Kafka", "Angular")), Map.class);
        long kafka = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Kafka", "dailyRate", 650, "status", "WON",
            "clientId", aliceClientId, "startDate", "2025-03-01", "techStack", "Kafka", "description", "Flux de paie."), Map.class));
        long angular = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Dev Angular", "dailyRate", 550, "status", "WON",
            "clientId", aliceClientId, "startDate", "2023-03-01", "techStack", "Angular", "description", "Front."), Map.class));

        Map<String, Object> sources = call(alice, HttpMethod.GET, "/users/cv-profiles/sources", null, Map.class).getBody();
        assertThat((List<String>) sources.get("skills")).containsExactly("Java", "Kafka", "Angular");
        assertThat((List<Map<String, Object>>) sources.get("missions")).extracting(mission -> ((Number) mission.get("id")).longValue())
            .containsExactly(kafka, angular);

        Map<String, Object> backend = call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", " Backend Kafka ", "headline", "Lead Kafka",
            "skills", List.of("Kafka", "Java"), "missions", List.of(Map.of("id", kafka, "text", "Flux de paie temps réel."))), Map.class).getBody();
        assertThat(backend).containsEntry("name", "Backend Kafka").containsEntry("isDefault", true).containsEntry("language", "FR");
        Map<String, Object> front = call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", "Front", "language", "EN"), Map.class).getBody();
        assertThat(front).containsEntry("isDefault", false).containsEntry("skills", null);
        long backendId = ((Number) backend.get("id")).longValue();
        long frontId = ((Number) front.get("id")).longValue();

        String drawn = text(call(alice, HttpMethod.GET, "/users/cv-profiles/" + backendId + "/dossier", null, byte[].class).getBody());
        assertThat(drawn).contains("Lead Kafka", "Kafka · Java", "Flux de paie temps réel.", "Alice Corp").doesNotContain("Dev Angular", "Angular");
        // The default draws the plain dossier download too.
        assertThat(text(call(alice, HttpMethod.GET, "/users/dossier", null, byte[].class).getBody())).contains("Flux de paie temps réel.");
        assertThat(text(call(alice, HttpMethod.GET, "/users/cv-profiles/" + frontId + "/dossier?layout=ANONYMOUS", null, byte[].class).getBody()))
            .contains("Skills profile", "Dev Angular").doesNotContain("Alice Corp");

        assertThat(call(alice, HttpMethod.PUT, "/users/cv-profiles/" + frontId + "/default", null, Map.class).getBody()).containsEntry("isDefault", true);
        call(alice, HttpMethod.PUT, "/users/cv-profiles/" + backendId, Map.of("name", "Backend", "summary", "Kafka avant tout."), Map.class);
        List<Map<String, Object>> listed = call(alice, HttpMethod.GET, "/users/cv-profiles", null, List.class).getBody();
        assertThat(listed).extracting(profile -> profile.get("name")).containsExactly("Backend", "Front");
        assertThat(listed.getFirst()).containsEntry("isDefault", false).containsEntry("summary", "Kafka avant tout.").containsEntry("skills", null);

        // Bob reaches none of them.
        assertThat(call(bob, HttpMethod.GET, "/users/cv-profiles", null, List.class).getBody()).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/users/cv-profiles/" + backendId + "/dossier", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, "/users/cv-profiles/" + backendId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", " "), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // The default deleted, the oldest one left takes its place; at most 20.
        assertThat(call(alice, HttpMethod.DELETE, "/users/cv-profiles/" + frontId, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/users/cv-profiles", null, List.class).getBody()).singleElement()
            .satisfies(profile -> assertThat((Map<String, Object>) profile).containsEntry("isDefault", true));
        for (int index = 0; index < 19; index++) {
            call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", "Profil " + index), Map.class);
        }
        ResponseEntity<Map> refused = call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", "De trop"), Map.class);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
