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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice downloads her dossier de compétences over HTTP, named and anonymized; it holds her missions only. */
class DossierIntegrationTest extends TwoAccountsIntegrationTest {

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void downloadsTheDossierNamedOrAnonymized() throws IOException {
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "status", "WON", "clientId", aliceClientId,
            "startDate", "2025-03-01", "techStack", "Java, Kafka"), Map.class);
        call(bob, HttpMethod.POST, "/projects", Map.of("role", "Bob's mission", "dailyRate", 500, "status", "WON"), Map.class);

        ResponseEntity<byte[]> named = call(alice, HttpMethod.GET, "/users/dossier?language=EN", null, byte[].class);
        assertThat(named.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
        assertThat(named.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(named.getHeaders().getContentDisposition().getFilename()).startsWith("skills-profile-");
        assertThat(text(named.getBody())).contains("Lead Java · Alice Corp", "Environment: Java, Kafka").doesNotContain("Bob's mission");

        byte[] anonymous = call(alice, HttpMethod.GET, "/users/dossier?layout=ANONYMOUS", null, byte[].class).getBody();
        assertThat(text(anonymous)).contains("Client communiqué sur demande").doesNotContain("Alice Corp", aliceEmail);

        assertThat(call(alice, HttpMethod.GET, "/users/dossier?layout=OTHER", null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchange("/users/dossier", HttpMethod.GET, HttpEntity.EMPTY, String.class).getStatusCode().is4xxClientError()).isTrue();
    }
}
