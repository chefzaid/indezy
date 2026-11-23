package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.ai.PostingPdfTextTest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice keeps her CV in her profile, replaces it, downloads it and removes it; Bob keeps none and reads none of hers. */
class ProfileCvIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    void keepsOneCvPerWorkspace() throws IOException {
        assertThat(call(alice, HttpMethod.GET, "/users/cv/info", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        byte[] first = PostingPdfTextTest.pdf(false, "Alice Martin", "Lead Java");
        ResponseEntity<Map> kept = upload("C:\\Users\\alice\\cv-alice.pdf", first);
        assertThat(kept.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(kept.getBody()).containsEntry("fileName", "cv-alice.pdf").containsEntry("contentType", "application/pdf")
            .containsEntry("sizeBytes", first.length);

        byte[] second = PostingPdfTextTest.pdf(false, "Alice Martin", "Lead Java", "Kafka");
        upload("cv-2026.pdf", second);
        assertThat(call(alice, HttpMethod.GET, "/users/cv/info", null, Map.class).getBody()).containsEntry("fileName", "cv-2026.pdf");
        ResponseEntity<byte[]> download = call(alice, HttpMethod.GET, "/users/cv", null, byte[].class);
        assertThat(download.getBody()).isEqualTo(second);
        assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo("cv-2026.pdf");

        assertThat(upload("notes.txt", "hello".getBytes(StandardCharsets.UTF_8)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        // A CV in Word's old format is kept as one; a file only named so is refused.
        assertThat(upload("cv-alice.doc", dev.swirlit.indezy.media.WordDocTextTest.cv()).getBody()).containsEntry("contentType", "application/msword");
        assertThat(upload("cv.doc", java.util.Arrays.copyOf(dev.swirlit.indezy.media.WordDocTextTest.cv(), 600)).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        upload("cv-2026.pdf", second);
        assertThat(call(bob, HttpMethod.GET, "/users/cv", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.GET, "/users/export", null, String.class).getBody()).contains("cv-2026.pdf");

        assertThat(call(alice, HttpMethod.DELETE, "/users/cv", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/users/cv", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<Map> upload(String name, byte[] content) {
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(alice.token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return exchange("/users/cv", HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
    }
}
