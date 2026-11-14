package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice asks a contact for a testimonial; the contact writes it through the link, without an account, once; Alice shows
 * it on her public profile. Bob can neither see nor change it, nor ask one of Alice's contacts.
 */
class TestimonialIntegrationTest extends TwoAccountsIntegrationTest {

    private <T> ResponseEntity<T> asAuthor(HttpMethod method, String token, Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Testimonial-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return exchange("/public/testimonials", method, new HttpEntity<>(body, headers), type);
    }

    @Test
    @SuppressWarnings("unchecked")
    void collectsApprovesAndShowsATestimonial() {
        long contact = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Léa", "lastName", "Martin", "email",
            "lea@acme.example", "clientId", aliceClientId), Map.class));
        assertThat(call(bob, HttpMethod.POST, "/testimonials", Map.of("contactId", contact), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> link = call(alice, HttpMethod.POST, "/testimonials", Map.of("contactId", contact), Map.class).getBody();
        String token = (String) link.get("token");
        Map<String, Object> asked = (Map<String, Object>) link.get("testimonial");
        assertThat(asked).containsEntry("recipientName", "Léa Martin").containsEntry("status", "REQUESTED");
        String testimonial = "/testimonials/" + asked.get("id");
        assertThat(call(alice, HttpMethod.PUT, testimonial + "/status", Map.of("status", "APPROVED"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        assertThat(asAuthor(HttpMethod.GET, token, null, Map.class).getBody()).containsEntry("answered", false)
            .containsEntry("recipientName", "Léa Martin");
        assertThat(asAuthor(HttpMethod.GET, "wrong", null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> answer = new HashMap<>(Map.of("authorName", "Léa Martin", "authorTitle", "CTO, Acme",
            "text", "Un consultant rigoureux, qui a mené la refonte en temps et en heure.", "consent", false));
        assertThat(asAuthor(HttpMethod.POST, token, answer, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        answer.put("consent", true);
        assertThat(asAuthor(HttpMethod.POST, token, answer, Map.class).getBody()).containsEntry("answered", true);
        assertThat(asAuthor(HttpMethod.POST, token, answer, String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(call(bob, HttpMethod.PUT, testimonial + "/status", Map.of("status", "APPROVED"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.PUT, testimonial + "/status", Map.of("status", "APPROVED"), Map.class).getBody())
            .containsEntry("status", "APPROVED").containsEntry("authorTitle", "CTO, Acme");
        call(alice, HttpMethod.PUT, "/users/public-profile", Map.of("enabled", true, "slug", "alice-testimonials"), Map.class);
        List<Map<String, Object>> shown = (List<Map<String, Object>>) exchange("/public/profiles/alice-testimonials", HttpMethod.GET,
            HttpEntity.EMPTY, Map.class).getBody().get("testimonials");
        assertThat(shown).singleElement().satisfies(entry -> assertThat(entry).containsEntry("authorName", "Léa Martin"));

        assertThat(call(bob, HttpMethod.GET, "/testimonials", null, List.class).getBody()).isEmpty();
        assertThat(call(alice, HttpMethod.GET, "/users/export", null, String.class).getBody()).contains("CTO, Acme");
        assertThat(call(alice, HttpMethod.DELETE, testimonial, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(asAuthor(HttpMethod.GET, token, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void asksOfSomeoneNamedButNotOfNoOne() {
        assertThat(call(alice, HttpMethod.POST, "/testimonials", Map.of("recipientName", "Paul Durand"), Map.class).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
        assertThat(call(alice, HttpMethod.POST, "/testimonials", Map.of("recipientName", " "), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.PUT, "/testimonials/999999/status", Map.of("status", "HIDDEN"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
