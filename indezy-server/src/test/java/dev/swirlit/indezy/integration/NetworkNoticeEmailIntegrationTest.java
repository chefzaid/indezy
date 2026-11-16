package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import dev.swirlit.indezy.service.NetworkNoticeEmailService;
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
 * Bob's network notifications by email, Resend replaced: only the kinds he chose, once unread an hour, grouped; given back
 * when Resend refuses it; a kind turned off is emailed no more; the mailbox's one-click unsubscribe stops them all.
 */
@TestPropertySource(properties = "indezy.mail.resend-api-key=re_test")
class NetworkNoticeEmailIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @MockitoBean
    private ResendMailer mailer;

    @Autowired
    private NetworkNoticeEmailService emails;

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

    private List<Email> toBob() throws InterruptedException {
        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer, atLeast(0)).send(sent.capture());
        return sent.getAllValues().stream().filter(email -> email.to().equals(bobEmail)).toList();
    }

    @Test
    @SuppressWarnings("unchecked")
    void emailsTheChosenKindsUnreadAfterAnHour() throws Exception {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("other");
        when(mailer.send(argThat(email -> email != null && bobEmail.equals(email.to())))).thenThrow(new MailException("Resend refused it"))
            .thenReturn("49a3999c");
        join(alice);
        String bobHandle = join(bob);
        Object post = call(bob, HttpMethod.POST, "/network/posts", Map.of("text", "Retour sur Kafka en production"), Map.class).getBody().get("id");

        // Nothing emailed by default; Bob chooses likes, follows stay in the app.
        assertThat(call(bob, HttpMethod.GET, "/network/notifications/settings", null, Map.class).getBody()).containsEntry("emailed", List.of());
        Map<String, Object> chosen = call(bob, HttpMethod.PUT, "/network/notifications/settings", Map.of("off", List.of(),
            "emailed", List.of("LIKE", "WARNED")), Map.class).getBody();
        assertThat(chosen).containsEntry("emailed", List.of("LIKE"));
        call(alice, HttpMethod.PUT, "/network/posts/" + post + "/like", null, Map.class);
        call(alice, HttpMethod.PUT, "/network/members/" + bobHandle + "/follow", null, Map.class);
        Instant now = Instant.now();

        emails.send(now);
        assertThat(toBob()).isEmpty();
        emails.send(now.plus(Duration.ofHours(2)));
        emails.send(now.plus(Duration.ofHours(2)));
        List<Email> sent = toBob();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(1).text()).contains("liked your post “Retour sur Kafka en production”").doesNotContain("followed you");
        emails.send(now.plus(Duration.ofHours(6)));
        assertThat(toBob()).hasSize(2);

        // A kind turned off is emailed no more; settings keep the emailed kinds when none are given.
        Map<String, Object> off = call(bob, HttpMethod.PUT, "/network/notifications/settings", Map.of("off", List.of("LIKE")), Map.class).getBody();
        assertThat(off).containsEntry("off", List.of("LIKE")).containsEntry("emailed", List.of());
        call(bob, HttpMethod.PUT, "/network/notifications/settings", Map.of("off", List.of(), "emailed", List.of("FOLLOW")), Map.class);

        // The mailbox's one-click unsubscribe stops every kind.
        String unsubscribe = sent.get(1).headers().get("List-Unsubscribe");
        String token = unsubscribe.substring(unsubscribe.lastIndexOf('/') + 1, unsubscribe.length() - 1);
        assertThat(post("/public/network-notifications/unsubscribe/" + token, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/public/network-notifications/unsubscribe/unknown", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat((List<Object>) call(bob, HttpMethod.GET, "/network/notifications/settings", null, Map.class).getBody().get("emailed")).isEmpty();
        flags.set(FeatureFlag.SOCIAL_NETWORK, false);
        assertThat(emails.send(now.plus(Duration.ofHours(30)))).isZero();
    }
}
