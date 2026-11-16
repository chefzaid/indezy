package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import dev.swirlit.indezy.service.NetworkMessageEmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bob's unread messages email, Resend replaced: sent once Alice's messages stay unread an hour, saying who and how many,
 * never the text; given back when Resend refuses it; at most every three hours; never for a muted conversation or once
 * read; stopped from Profile → Notifications or by the mailbox's one-click unsubscribe.
 */
@TestPropertySource(properties = "indezy.mail.resend-api-key=re_test")
class NetworkMessageEmailIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @MockitoBean
    private ResendMailer mailer;

    @Autowired
    private NetworkMessageEmailService emails;

    @Autowired
    private FeatureFlagService flags;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    /** The emails sent to Bob so far. */
    private List<Email> toBob() throws InterruptedException {
        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer, atLeast(0)).send(sent.capture());
        return sent.getAllValues().stream().filter(email -> email.to().equals(bobEmail)).toList();
    }

    private void send(Object conversation, String text) {
        call(alice, HttpMethod.POST, "/network/conversations/" + conversation + "/messages", Map.of("text", text), Map.class);
    }

    @Test
    void emailsTheMessagesUnreadAfterAnHour() throws Exception {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("other");
        when(mailer.send(argThat(email -> email != null && bobEmail.equals(email.to())))).thenThrow(new MailException("Resend refused it"))
            .thenReturn("49a3999c");
        String aliceHandle = join(alice);
        String bobHandle = join(bob);
        call(bob, HttpMethod.PUT, "/network/members/" + aliceHandle + "/follow", null, Map.class);
        Object conversation = call(alice, HttpMethod.POST, "/network/conversations", Map.of("handle", bobHandle), Map.class).getBody().get("id");
        send(conversation, "Bonjour Bob");
        send(conversation, "Une mission Kafka à Lyon ?");
        Instant now = Instant.now();

        // Not an hour old yet: nothing. Then refused once by Resend, given back, and sent at the next run.
        emails.send(now);
        assertThat(toBob()).isEmpty();
        emails.send(now.plus(Duration.ofHours(2)));
        emails.send(now.plus(Duration.ofHours(2)));
        List<Email> sent = toBob();
        assertThat(sent).hasSize(2);
        Email email = sent.get(1);
        assertThat(email.subject()).isEqualTo("2 unread messages on Indezy");
        assertThat(email.text()).contains("alice Test · 2 messages").contains("/network/messages").doesNotContain("Kafka", "Bonjour Bob");
        assertThat(email.headers()).containsKey("List-Unsubscribe").containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");

        // Emailed once: not again for the same messages, and at most every three hours for new ones.
        emails.send(now.plus(Duration.ofHours(2)));
        send(conversation, "Tu es là ?");
        emails.send(now.plus(Duration.ofHours(4)));
        assertThat(toBob()).hasSize(2);
        emails.send(now.plus(Duration.ofHours(6)));
        assertThat(toBob()).hasSize(3);
        assertThat(toBob().get(2).subject()).isEqualTo("1 unread message on Indezy");

        // Read, muted or turned off, no email; the mailbox's one-click unsubscribe turns them off too.
        send(conversation, "Toujours rien ?");
        call(bob, HttpMethod.PUT, "/network/conversations/" + conversation + "/settings", Map.of("muted", true), Map.class);
        emails.send(now.plus(Duration.ofHours(10)));
        assertThat(toBob()).hasSize(3);
        call(bob, HttpMethod.PUT, "/network/conversations/" + conversation + "/settings", Map.of("muted", false), Map.class);
        Map<String, Object> settings = new HashMap<>(call(bob, HttpMethod.GET, "/users/notifications", null, Map.class).getBody());
        assertThat(settings).containsEntry("messageEmails", true);
        settings.put("messageEmails", false);
        assertThat(call(bob, HttpMethod.PUT, "/users/notifications", settings, Map.class).getBody()).containsEntry("messageEmails", false);
        emails.send(now.plus(Duration.ofHours(14)));
        assertThat(toBob()).hasSize(3);
        settings.put("messageEmails", true);
        call(bob, HttpMethod.PUT, "/users/notifications", settings, Map.class);
        String unsubscribe = email.headers().get("List-Unsubscribe");
        String token = unsubscribe.substring(unsubscribe.lastIndexOf('/') + 1, unsubscribe.length() - 1);
        assertThat(post("/public/network-messages/unsubscribe/" + token, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/public/network-messages/unsubscribe/unknown", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(bob, HttpMethod.GET, "/users/notifications", null, Map.class).getBody()).containsEntry("messageEmails", false);
        emails.send(now.plus(Duration.ofHours(18)));
        assertThat(toBob()).hasSize(3);

        // With the network off, none at all.
        flags.set(FeatureFlag.SOCIAL_NETWORK, false);
        assertThat(emails.send(now.plus(Duration.ofHours(30)))).isZero();
    }
}
