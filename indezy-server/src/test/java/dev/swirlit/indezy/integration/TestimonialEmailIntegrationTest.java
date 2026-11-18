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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alice asks an external person for a testimonial by their name and email: Indezy emails them their own link, Resend
 * replaced, which they answer without an account. A contact with an email is emailed too; an email that cannot go
 * leaves the link to give by hand.
 */
@TestPropertySource(properties = "indezy.mail.resend-api-key=re_test")
class TestimonialEmailIntegrationTest extends TwoAccountsIntegrationTest {

    @MockitoBean
    private ResendMailer mailer;

    private ResponseEntity<Map> openAsAuthor(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Testimonial-Token", token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return exchange("/public/testimonials", HttpMethod.GET, new HttpEntity<>(null, headers), Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emailsAnExternalPersonTheirLink() throws Exception {
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-1");

        // An external person is asked with their first name, last name and email.
        assertThat(call(alice, HttpMethod.POST, "/testimonials", Map.of("recipientFirstName", "Marie", "recipientLastName", "Durand"),
            String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.POST, "/testimonials", Map.of("recipientFirstName", "Marie", "recipientLastName", "Durand",
            "recipientEmail", "not-an-email"), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(alice, HttpMethod.POST, "/testimonials", Map.of("recipientFirstName", "Marie", "recipientLastName", "Durand",
            "recipientEmail", "marie@acme"), String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Map> asked = call(alice, HttpMethod.POST, "/testimonials", Map.of("recipientFirstName", " Marie ",
            "recipientLastName", "Durand", "recipientEmail", "Marie.Durand@Acme.example"), Map.class);
        assertThat(asked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(asked.getBody()).containsEntry("emailed", true);
        Map<String, Object> testimonial = (Map<String, Object>) asked.getBody().get("testimonial");
        assertThat(testimonial).containsEntry("recipientName", "Marie Durand").containsEntry("recipientEmail", "marie.durand@acme.example");
        String token = (String) asked.getBody().get("token");

        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer).send(sent.capture());
        Email email = sent.getValue();
        assertThat(email.to()).isEqualTo("marie.durand@acme.example");
        assertThat(email.subject()).contains("alice Test");
        assertThat(email.text()).contains("Marie Durand", "/testimonial#" + token);
        assertThat(email.html()).contains("/testimonial#" + token);
        assertThat(email.idempotencyKey()).isEqualTo("testimonial-request-" + testimonial.get("id"));
        // The link they received opens the request, without an account.
        assertThat(openAsAuthor(token).getBody()).containsEntry("recipientName", "Marie Durand").containsEntry("answered", false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emailsAContactWithAnAddressAndKeepsTheLinkWhenTheEmailCannotGo() throws Exception {
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenThrow(new MailException("Resend refused it"));
        long contact = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Léa", "lastName", "Martin", "email",
            "lea@acme.example", "clientId", aliceClientId), Map.class));

        Map<String, Object> link = call(alice, HttpMethod.POST, "/testimonials", Map.of("contactId", contact), Map.class).getBody();
        assertThat(link).containsEntry("emailed", false);
        assertThat((Map<String, Object>) link.get("testimonial")).containsEntry("recipientEmail", "lea@acme.example");
        assertThat(openAsAuthor((String) link.get("token")).getStatusCode()).isEqualTo(HttpStatus.OK);

        // A name alone still gives a link to pass on by hand, nothing emailed.
        Map<String, Object> byHand = call(alice, HttpMethod.POST, "/testimonials", Map.of("recipientName", "Paul"), Map.class).getBody();
        assertThat(byHand).containsEntry("emailed", false);
        assertThat((Map<String, Object>) byHand.get("testimonial")).containsEntry("recipientEmail", null);
    }

    @Test
    void sendsNothingWithoutAMailer() throws Exception {
        when(mailer.configured()).thenReturn(false);
        Map<String, Object> link = call(bob, HttpMethod.POST, "/testimonials", Map.of("recipientFirstName", "Marie", "recipientLastName",
            "Durand", "recipientEmail", "marie@acme.example"), Map.class).getBody();
        assertThat(link).containsEntry("emailed", false);
        verify(mailer, never()).send(any());
    }

    @Test
    void emailsTwentyRequestsADayAtMost() throws Exception {
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-1");
        for (int i = 0; i < 20; i++) {
            assertThat(call(alice, HttpMethod.POST, "/testimonials", Map.of("recipientFirstName", "Marie", "recipientLastName", "Durand",
                "recipientEmail", "marie" + i + "@acme.example"), String.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        assertThat(call(alice, HttpMethod.POST, "/testimonials", Map.of("recipientFirstName", "Marie", "recipientLastName", "Durand",
            "recipientEmail", "marie20@acme.example"), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // A link to give by hand is still asked.
        assertThat(call(alice, HttpMethod.POST, "/testimonials", Map.of("recipientName", "Paul"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
    }
}
