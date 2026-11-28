package dev.swirlit.indezy.integration;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice shares a CV profile through a link an ESN opens without an account; she sees it opened, then revokes it. */
class CvShareIntegrationTest extends TwoAccountsIntegrationTest {

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static HttpEntity<Void> linked(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Cv-Token", token);
        return new HttpEntity<>(headers);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sharesACvProfileThroughALinkOpenedWithoutAnAccount() throws IOException {
        call(alice, HttpMethod.PUT, "/users/profile", Map.of("firstName", "Alice", "lastName", "Martin", "email", aliceEmail, "skills",
            List.of("Java", "Kafka")), Map.class);
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Kafka", "dailyRate", 650, "status", "WON", "clientId", aliceClientId,
            "startDate", "2025-03-01", "techStack", "Kafka", "description", "Flux."), Map.class);
        long profile = idOf(call(alice, HttpMethod.POST, "/users/cv-profiles", Map.of("name", "Backend", "headline", "Lead Kafka"), Map.class));

        Map<String, Object> created = call(alice, HttpMethod.POST, "/users/cv-shares", Map.of("profileId", profile, "recipient", " ESN Alpha ",
            "layout", "ANONYMOUS", "days", 3), Map.class).getBody();
        String token = (String) created.get("token");
        assertThat(token).isNotBlank();
        assertThat((Map<String, Object>) created.get("share")).containsEntry("recipient", "ESN Alpha").containsEntry("profileName", "Backend")
            .containsEntry("active", true).containsEntry("openCount", 0);

        // Opened without an account: initials only, anonymized; the dossier downloads as a PDF or a Word document.
        Map<String, Object> opened = exchange("/public/cv-shares", HttpMethod.GET, linked(token), Map.class).getBody();
        assertThat(opened).containsEntry("name", "A. M.").containsEntry("headline", "Lead Kafka").containsEntry("layout", "ANONYMOUS");
        ResponseEntity<byte[]> pdf = exchange("/public/cv-shares/dossier", HttpMethod.GET, linked(token), byte[].class);
        assertThat(text(pdf.getBody())).contains("Lead Kafka", "Client communiqué sur demande").doesNotContain("Alice Corp", aliceEmail);
        assertThat(exchange("/public/cv-shares/dossier?format=DOCX", HttpMethod.GET, linked(token), byte[].class).getHeaders()
            .getContentDisposition().getFilename()).endsWith(".docx");
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/users/cv-shares", null, List.class).getBody()).singleElement()
            .satisfies(share -> assertThat(share).containsEntry("openCount", 1).containsEntry("downloadCount", 2));

        // A wrong token, another workspace's profile, and a revoked link: not found.
        assertThat(exchange("/public/cv-shares", HttpMethod.GET, linked("nope"), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.POST, "/users/cv-shares", Map.of("profileId", profile, "recipient", "Me"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.POST, "/users/cv-shares", Map.of("profileId", profile, "recipient", "Me", "days", 31), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        long id = ((Number) ((Map<String, Object>) created.get("share")).get("id")).longValue();
        assertThat(call(bob, HttpMethod.PUT, "/users/cv-shares/" + id + "/revoke", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.PUT, "/users/cv-shares/" + id + "/revoke", null, Map.class).getBody()).containsEntry("active", false);
        assertThat(exchange("/public/cv-shares", HttpMethod.GET, linked(token), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // A profile deleted takes its links with it.
        String other = (String) call(alice, HttpMethod.POST, "/users/cv-shares", Map.of("profileId", profile, "recipient", "ESN Beta"), Map.class)
            .getBody().get("token");
        call(alice, HttpMethod.DELETE, "/users/cv-profiles/" + profile, null, String.class);
        assertThat(exchange("/public/cv-shares", HttpMethod.GET, linked(other), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/users/cv-shares", null, List.class).getBody()).isEmpty();
    }
}
