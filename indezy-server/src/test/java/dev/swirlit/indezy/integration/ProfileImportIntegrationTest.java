package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.service.LinkedInProfileImportTest;
import dev.swirlit.indezy.service.LinkedInProfilePdfTest;
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

/** Alice reads her LinkedIn archive or profile PDF into her profile's fields, nothing saved; a file that is neither is refused. */
class ProfileImportIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    void readsLinkedInsArchiveWithoutSavingIt() throws IOException {
        ResponseEntity<Map> read = upload(LinkedInProfileImportTest.archive(), alice.token());
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody()).containsEntry("firstName", "Alice").containsEntry("city", "Lyon").containsEntry("company", "Alice Conseil");
        assertThat(call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody()).doesNotContainEntry("company", "Alice Conseil");

        // LinkedIn's Save to PDF goes the same way, told apart by its first bytes.
        ResponseEntity<Map> pdf = upload(LinkedInProfilePdfTest.profile(), alice.token());
        assertThat(pdf.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pdf.getBody()).containsEntry("lastName", "Martin").containsEntry("company", "Alice Conseil");

        assertThat(upload("hello".getBytes(StandardCharsets.UTF_8), alice.token()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // A CV read without AI.
        ResponseEntity<Map> cv = upload("/users/profile-import/cv", "cv-alice.doc", dev.swirlit.indezy.media.WordDocTextTest.cv(), alice.token());
        assertThat(cv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cv.getBody()).containsEntry("firstName", "Alice").containsEntry("lastName", "Martin").containsEntry("city", null);
        assertThat(upload("/users/profile-import/cv", "notes.txt", "hello".getBytes(StandardCharsets.UTF_8), alice.token()).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(LinkedInProfileImportTest.archive(), null).getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<Map> upload(byte[] content, String token) {
        return upload("/users/profile-import/linkedin", "Basic_LinkedInDataExport.zip", content, token);
    }

    private ResponseEntity<Map> upload(String path, String name, byte[] content, String token) {
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return exchange(path, HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
    }
}
