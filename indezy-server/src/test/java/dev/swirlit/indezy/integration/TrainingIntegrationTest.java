package dev.swirlit.indezy.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Alice keeps her trainings over HTTP; Bob sees and changes none of them, and her data export carries them. */
class TrainingIntegrationTest extends TwoAccountsIntegrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void keepsTheWorkspacesTrainings() {
        Map<String, Object> details = new HashMap<>(Map.of("title", "Certified Kubernetes Administrator", "hours", 21,
            "cost", 1800, "fund", "FIF_PL", "certification", "CKA"));
        Map<String, Object> created = call(alice, HttpMethod.POST, "/trainings", details, Map.class).getBody();
        assertThat(created).containsEntry("fundingStatus", "TO_REQUEST").containsEntry("certificationValidity", "NO_EXPIRY");
        String training = "/trainings/" + created.get("id");

        details.put("fundingStatus", "ACCEPTED");
        assertThat(call(alice, HttpMethod.PUT, training, details, Map.class).getBody()).containsEntry("fundingStatus", "ACCEPTED");
        assertThat(call(alice, HttpMethod.GET, "/trainings", null, List.class).getBody()).hasSize(1);

        assertThat(call(bob, HttpMethod.GET, "/trainings", null, List.class).getBody()).isEmpty();
        assertThat(call(bob, HttpMethod.PUT, training, details, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.DELETE, training, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        details.put("title", " ");
        assertThat(call(alice, HttpMethod.POST, "/trainings", details, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(call(alice, HttpMethod.GET, "/users/export", null, String.class).getBody()).contains("Certified Kubernetes Administrator");

        // The valid certification shows on Alice's public profile once she shows it.
        call(alice, HttpMethod.PUT, "/users/public-profile", Map.of("enabled", true, "slug", "alice-trainings"), Map.class);
        assertThat(exchange("/public/profiles/alice-trainings", HttpMethod.GET, org.springframework.http.HttpEntity.EMPTY, Map.class)
            .getBody()).containsEntry("certifications", List.of("CKA"));
        assertThat(call(alice, HttpMethod.DELETE, training, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.GET, "/trainings", null, List.class).getBody()).isEmpty();
    }
}
