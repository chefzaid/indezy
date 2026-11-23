package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.pdf.FontCheckTest;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice prints her documents in a font she uploads; a file that is not a font is refused; she goes back to a bundled one. */
class DocumentFontIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    void printsTheDocumentsInAnUploadedFont() {
        ResponseEntity<Map> uploaded = upload(FontCheckTest.serif(), FontCheckTest.serifBold());
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(uploaded.getBody()).containsEntry("font", "CUSTOM").containsEntry("fontFamily", "Liberation Serif");
        assertThat(uploaded.getBody().get("fontFileId")).isNotNull();
        // Saving the form keeps the font, which the form never sets.
        Map<String, Object> identity = new java.util.HashMap<>(call(alice, HttpMethod.GET, "/users/billing", null, Map.class).getBody());
        identity.put("fontFileId", null);
        assertThat(call(alice, HttpMethod.PUT, "/users/billing", identity, Map.class).getBody()).containsEntry("font", "CUSTOM")
            .containsEntry("fontFamily", "Liberation Serif");

        assertThat(upload("hello".getBytes(StandardCharsets.UTF_8), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(bob, HttpMethod.GET, "/users/billing", null, Map.class).getBody()).containsEntry("font", "SANS");

        Map<String, Object> removed = call(alice, HttpMethod.DELETE, "/users/billing/font", null, Map.class).getBody();
        assertThat(removed).containsEntry("font", "SANS").containsEntry("fontFileId", null);
        // Without an uploaded font, choosing it falls back to the sans-serif one.
        identity.put("font", "CUSTOM");
        assertThat(call(alice, HttpMethod.PUT, "/users/billing", identity, Map.class).getBody()).containsEntry("font", "SANS");
    }

    private ResponseEntity<Map> upload(byte[] regular, byte[] bold) {
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", resource(regular, "MaPolice-Regular.ttf"));
        if (bold != null) {
            form.add("bold", resource(bold, "MaPolice-Bold.ttf"));
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(alice.token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return exchange("/users/billing/font", HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
    }

    private static ByteArrayResource resource(byte[] content, String name) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }
}
