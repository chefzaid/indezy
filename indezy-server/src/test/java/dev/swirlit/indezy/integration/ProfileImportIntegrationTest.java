package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.service.LinkedInProfileImportTest;
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

/** Alice reads her LinkedIn archive into her profile's fields, nothing saved; a file that is not one is refused. */
class ProfileImportIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    void readsLinkedInsArchiveWithoutSavingIt() throws IOException {
        ResponseEntity<Map> read = upload(LinkedInProfileImportTest.archive(), alice.token());
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody()).containsEntry("firstName", "Alice").containsEntry("city", "Lyon").containsEntry("company", "Alice Conseil");
        assertThat(call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody()).doesNotContainEntry("company", "Alice Conseil");

        assertThat(upload("hello".getBytes(StandardCharsets.UTF_8), alice.token()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(LinkedInProfileImportTest.archive(), null).getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<Map> upload(byte[] content, String token) {
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "Basic_LinkedInDataExport.zip";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return exchange("/users/profile-import/linkedin", HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);
    }
}
