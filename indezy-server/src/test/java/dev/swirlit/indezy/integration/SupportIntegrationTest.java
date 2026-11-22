package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.LoginRequest;
import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.ResendMailer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Alice writes to support from the app; an administrator answers, which emails her; Bob sees neither. */
@TestPropertySource(properties = {"indezy.admin.emails=" + AdminIntegrationTest.CHIEF, "indezy.mail.resend-api-key=re_test"})
class SupportIntegrationTest extends TwoAccountsIntegrationTest {

    @MockitoBean
    private ResendMailer mailer;

    private Account chief() {
        LoginRequest request = new LoginRequest();
        request.setEmail(AdminIntegrationTest.CHIEF);
        request.setPassword(PASSWORD);
        ResponseEntity<Map> signedIn = post("/auth/login", request);
        if (signedIn.getStatusCode() != HttpStatus.OK) {
            registerAs(AdminIntegrationTest.CHIEF);
            signedIn = post("/auth/login", request);
        }
        Map<?, ?> user = (Map<?, ?>) signedIn.getBody().get("user");
        return new Account((String) signedIn.getBody().get("token"), ((Number) user.get("id")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void answersAMembersRequestAndTellsThem() throws Exception {
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-1");
        assertThat(call(alice, HttpMethod.POST, "/support/requests", Map.of("kind", "PROBLEM", "subject", " "), String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Map> sent = call(alice, HttpMethod.POST, "/support/requests", Map.of("kind", "PROBLEM", "subject", " Invoice PDF empty ",
            "description", "The PDF of F2026-0003 is blank.", "context", "Page: /invoices · Chrome 153 · 1280×900 · en"), Map.class);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(sent.getBody()).containsEntry("subject", "Invoice PDF empty").containsEntry("status", "OPEN").containsEntry("answer", null);
        long id = ((Number) sent.getBody().get("id")).longValue();

        assertThat(call(bob, HttpMethod.GET, "/support/requests", null, List.class).getBody()).isEmpty();
        assertThat(call(bob, HttpMethod.GET, "/admin/support/requests", null, String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Account chief = chief();
        List<Map<String, Object>> received = call(chief, HttpMethod.GET, "/admin/support/requests", null, List.class).getBody();
        Map<String, Object> mine = received.stream().filter(entry -> ((Number) ((Map<String, Object>) entry.get("request")).get("id")).longValue() == id)
            .findFirst().orElseThrow();
        assertThat(mine).containsEntry("email", aliceEmail);
        assertThat(call(bob, HttpMethod.PUT, "/admin/support/requests/" + id, Map.of("answer", "x", "close", false), String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(chief, HttpMethod.PUT, "/admin/support/requests/" + id, Map.of("answer", "Fixed in today's release.", "close", true),
            Map.class).getBody()).containsEntry("status", "CLOSED").containsEntry("answer", "Fixed in today's release.");

        List<Map<String, Object>> list = call(alice, HttpMethod.GET, "/support/requests", null, List.class).getBody();
        assertThat(list).hasSize(1);
        assertThat(list.getFirst()).containsEntry("answer", "Fixed in today's release.").containsEntry("context",
            "Page: /invoices · Chrome 153 · 1280×900 · en");
        ArgumentCaptor<Email> email = ArgumentCaptor.forClass(Email.class);
        verify(mailer).send(email.capture());
        assertThat(email.getValue().to()).isEqualTo(aliceEmail);
        assertThat(email.getValue().text()).contains("Invoice PDF empty", "/support").doesNotContain("Fixed in today's release.");
    }

    @Test
    void takesTenRequestsADayAtMost() {
        for (int index = 0; index < 10; index++) {
            call(alice, HttpMethod.POST, "/support/requests", Map.of("kind", "QUESTION", "subject", "Q" + index, "description", "?"), Map.class);
        }
        assertThat(call(alice, HttpMethod.POST, "/support/requests", Map.of("kind", "QUESTION", "subject", "Q", "description", "?"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
