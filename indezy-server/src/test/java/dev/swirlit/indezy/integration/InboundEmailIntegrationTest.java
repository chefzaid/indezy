package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.ResendMailer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Answers to Alice's email come back through Indezy, Resend replaced: the email carries a reply address, and Resend's
 * signed webhook for an answer marks it answered, records the answer and forwards it to her, once.
 */
@TestPropertySource(properties = {"indezy.mail.resend-api-key=re_test", "indezy.mail.reply-domain=replies.example",
    "indezy.mail.inbound-secret=" + InboundEmailIntegrationTest.SECRET})
class InboundEmailIntegrationTest extends TwoAccountsIntegrationTest {

    static final String SECRET = "whsec_YSB3ZWJob29rIHNpZ25pbmcga2V5";

    @MockitoBean
    private ResendMailer mailer;

    private org.springframework.http.ResponseEntity<String> deliver(String id, String body, boolean signed) throws Exception {
        long at = Instant.now().getEpochSecond();
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(SECRET.substring("whsec_".length())), "HmacSHA256"));
        String signature = "v1," + Base64.getEncoder().encodeToString(mac.doFinal((id + "." + at + "." + body).getBytes(StandardCharsets.UTF_8)));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("svix-id", id);
        headers.set("svix-timestamp", Long.toString(at));
        headers.set("svix-signature", signed ? signature : "v1,AAAA");
        return exchange("/public/emails/inbound", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void marksTheEmailAnsweredAndForwardsTheAnswerOnce() throws Exception {
        long contact = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Léa", "lastName", "Martin", "email",
            "lea@acme.example", "clientId", aliceClientId), Map.class));
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-1");
        long exchangeId = ((Number) call(alice, HttpMethod.POST, "/contacts/" + contact + "/emails", Map.of("subject", "Mission Java", "body",
            "Bonjour Léa,", "language", "FR"), Map.class).getBody().get("id")).longValue();
        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer).send(sent.capture());
        String token = jdbcTemplate.queryForObject("SELECT reply_token FROM contact_interactions WHERE id = ?", String.class, exchangeId);
        assertThat(token).hasSize(40).matches("[0-9a-f]+");
        assertThat(sent.getValue().replyTo()).isEqualTo("reply+" + token + "@replies.example");

        when(mailer.received("in-1")).thenReturn(new ResendMailer.Received("Léa Martin <lea@acme.example>", "Re: Mission Java",
            "Bonjour, oui, appelons-nous jeudi."));
        String body = "{\"type\":\"email.received\",\"created_at\":\"2026-11-24T10:00:00Z\",\"data\":{\"email_id\":\"in-1\"," +
            "\"from\":\"lea@acme.example\",\"to\":[\"Indezy <reply+" + token + "@Replies.example>\"],\"subject\":\"Re: Mission Java\"}}";
        assertThat(deliver("msg_1", body, false).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(deliver("msg_1", body, true).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // Delivered again (a retry): counted once.
        assertThat(deliver("msg_1b", body, true).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> exchanges = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/contacts/" + contact + "/interactions",
            null, Map.class).getBody().get("interactions");
        assertThat(exchanges).anySatisfy(item -> assertThat(item).containsEntry("direction", "INCOMING").containsEntry("summary", "Re: Mission Java"));
        assertThat(exchanges).anySatisfy(item -> assertThat(item).containsEntry("id", (int) exchangeId).containsEntry("replied", true));
        assertThat(exchanges).filteredOn(item -> "INCOMING".equals(item.get("direction"))).hasSize(1);
        ArgumentCaptor<Email> forwarded = ArgumentCaptor.forClass(Email.class);
        verify(mailer, times(2)).send(forwarded.capture());
        Email forward = forwarded.getAllValues().getLast();
        assertThat(forward.to()).isEqualTo(aliceEmail);
        assertThat(forward.replyTo()).isEqualTo("lea@acme.example");
        assertThat(forward.text()).contains("appelons-nous jeudi", "Léa Martin");

        // Another address, or an event that is not an answer, changes nothing.
        String other = body.replace("in-1", "in-2").replace(token, "0".repeat(40));
        assertThat(deliver("msg_2", other, true).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(mailer, times(2)).send(any());
    }
}
