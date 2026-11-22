package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alice emails a contact through Indezy, Resend replaced: the email goes in her name with answers to her, is recorded
 * as an exchange from her, and with open tracking asked tells her when it is opened, scanners and late opens aside.
 */
@TestPropertySource(properties = "indezy.mail.resend-api-key=re_test")
class ContactEmailIntegrationTest extends TwoAccountsIntegrationTest {

    @MockitoBean
    private ResendMailer mailer;

    private ResponseEntity<byte[]> pixel(String token) {
        return exchange("/public/emails/" + token + ".gif", HttpMethod.GET, HttpEntity.EMPTY, byte[].class);
    }

    private Map<String, Object> opened(long exchangeId) {
        return jdbcTemplate.queryForMap("SELECT email_opens, email_opened_at FROM contact_interactions WHERE id = ?", exchangeId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emailsAContactAndTellsWhenItIsOpened() throws Exception {
        long contact = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Léa", "lastName", "Martin", "email",
            "lea@acme.example", "clientId", aliceClientId), Map.class));
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Java Senior", "dailyRate", 650, "status", "INTERVIEW",
            "clientId", aliceClientId), Map.class));
        Map<String, Object> message = Map.of("projectId", project, "subject", "Mission Java Senior", "body",
            "Bonjour Léa,\n\nJe reviens vers vous.\nBien à vous,\n\nAlice <b>", "track", true, "copy", true, "language", "EN");

        // Without a Resend key, nothing can go.
        when(mailer.configured()).thenReturn(false);
        assertThat(call(alice, HttpMethod.GET, "/contacts/emails/status", null, Map.class).getBody()).containsEntry("available", false)
            .containsEntry("sentToday", 0).containsEntry("dailyMax", 50);
        assertThat(call(alice, HttpMethod.POST, "/contacts/" + contact + "/emails", message, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-1");
        Map<String, Object> sent = call(alice, HttpMethod.POST, "/contacts/" + contact + "/emails", message, Map.class).getBody();
        assertThat(sent).containsEntry("channel", "EMAIL").containsEntry("direction", "OUTGOING").containsEntry("replied", null)
            .containsEntry("summary", "Mission Java Senior").containsEntry("projectId", ((Number) project).intValue());
        assertThat((Map<String, Object>) sent.get("email")).containsEntry("tracked", true).containsEntry("opens", 0)
            .containsEntry("openedAt", null);
        long exchangeId = ((Number) sent.get("id")).longValue();

        ArgumentCaptor<Email> emails = ArgumentCaptor.forClass(Email.class);
        verify(mailer, times(2)).send(emails.capture());
        Email email = emails.getAllValues().getFirst();
        String token = jdbcTemplate.queryForObject("SELECT email_token FROM contact_interactions WHERE id = ?", String.class, exchangeId);
        assertThat(token).hasSize(43);
        assertThat(email.to()).isEqualTo("lea@acme.example");
        assertThat(email.replyTo()).isEqualTo(aliceEmail);
        assertThat(email.senderName()).isEqualTo("alice Test");
        assertThat(email.subject()).isEqualTo("Mission Java Senior");
        assertThat(email.html()).contains("<p>Bonjour Léa,</p>", "Je reviens vers vous.<br>Bien à vous,", "Alice &lt;b&gt;",
            "which tells them when it is opened", "/api/public/emails/" + token + ".gif");
        assertThat(email.text()).contains("Je reviens vers vous.", "which tells them when it is opened").doesNotContain(token);
        Email copy = emails.getAllValues().get(1);
        assertThat(copy.to()).isEqualTo(aliceEmail);
        assertThat(copy.subject()).isEqualTo("Copy: Mission Java Senior");
        assertThat(copy.html()).contains("Léa Martin").doesNotContain(token, "<img");

        // The exchange is on the contact and the opportunity, and counts as an email waiting for an answer.
        Map<String, Object> list = call(alice, HttpMethod.GET, "/contacts/" + contact + "/interactions", null, Map.class).getBody();
        assertThat((List<Map<String, Object>>) list.get("interactions")).hasSize(1);
        assertThat(call(alice, HttpMethod.GET, "/contacts/emails/status", null, Map.class).getBody()).containsEntry("sentToday", 1);

        // A scanner fetching the image at once counts nothing; the recipient's opens later do, the first one dated.
        assertThat(pixel(token).getHeaders().getContentType().toString()).isEqualTo("image/gif");
        assertThat(pixel(token).getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(opened(exchangeId)).containsEntry("email_opens", 0).containsEntry("email_opened_at", null);
        jdbcTemplate.update("UPDATE contact_interactions SET email_sent_at = email_sent_at - INTERVAL '1' MINUTE WHERE id = ?", exchangeId);
        pixel(token);
        pixel(token);
        assertThat(opened(exchangeId)).containsEntry("email_opens", 2);
        assertThat(opened(exchangeId).get("email_opened_at")).isNotNull();
        List<Map<String, Object>> exchanges = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/contacts/" + contact + "/interactions",
            null, Map.class).getBody().get("interactions");
        assertThat((Map<String, Object>) exchanges.getFirst().get("email")).containsEntry("opens", 2).doesNotContainEntry("openedAt", null);
        // A month later, opens are no longer counted; an unknown or malformed token gets the same image.
        jdbcTemplate.update("UPDATE contact_interactions SET email_sent_at = email_sent_at - INTERVAL '31' DAY WHERE id = ?", exchangeId);
        pixel(token);
        assertThat(opened(exchangeId)).containsEntry("email_opens", 2);
        assertThat(pixel("x".repeat(43)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pixel("short").getBody()).isEqualTo(pixel(token).getBody());

        // Without tracking: no image and no word of it; nor a copy unless asked.
        reset(mailer);
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-2");
        Map<String, Object> plain = call(alice, HttpMethod.POST, "/contacts/" + contact + "/emails", Map.of("subject", "Merci",
            "body", "Merci pour l'échange."), Map.class).getBody();
        assertThat((Map<String, Object>) plain.get("email")).containsEntry("tracked", false);
        verify(mailer).send(emails.capture());
        assertThat(emails.getValue().html()).doesNotContain("<img", "informe de son ouverture").contains("via Indezy");

        // Bob cannot email Alice's contact; a contact without an address cannot be emailed; a refused email records nothing.
        assertThat(call(bob, HttpMethod.POST, "/contacts/" + contact + "/emails", message, String.class).getStatusCode())
            .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        long noEmail = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Paul", "lastName", "Durand", "clientId",
            aliceClientId), Map.class));
        assertThat(call(alice, HttpMethod.POST, "/contacts/" + noEmail + "/emails", message, String.class).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.POST, "/contacts/" + contact + "/emails", Map.of("subject", " ", "body", "x"), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        when(mailer.send(any())).thenThrow(new MailException("Resend answered 422: validation_error"));
        assertThat(call(alice, HttpMethod.POST, "/contacts/" + contact + "/emails", message, String.class).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM contact_interactions WHERE contact_id = ?", Integer.class, contact))
            .isEqualTo(2);
    }
}
