package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.service.FeatureFlagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Invitations over HTTP, Resend replaced: Alice invites a contact once, who reads the invitation from its link and
 * declines every invitation; no member invites that address again. Another contact she invites joins, and she is told;
 * a contact already a member reads as one.
 */
@TestPropertySource(properties = "indezy.mail.resend-api-key=re_test")
class NetworkInvitationsIntegrationTest extends TwoAccountsIntegrationTest {

    private static final Map<String, Object> ALL_SHOWN = Map.of("photo", true, "headline", true, "stack", true, "location", true,
        "availability", true, "languages", true, "remote", false);

    @Autowired
    private FeatureFlagService flags;

    @MockitoBean
    private ResendMailer mailer;

    @AfterEach
    void reopenTheNetwork() {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
    }

    private String join(Account account) {
        String handle = "member_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        call(account, HttpMethod.PUT, "/network/membership", Map.of("handle", handle, "fields", ALL_SHOWN), Map.class);
        return handle;
    }

    private long contact(Account owner, String firstName, String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", "Test");
        body.put("clientId", owner == alice ? aliceClientId : idOf(call(owner, HttpMethod.POST, "/clients",
            Map.of("companyName", "Bob Corp", "city", "Lyon", "isFinal", true, "freelanceId", owner.freelanceId()), Map.class)));
        body.put("freelanceId", owner.freelanceId());
        if (email != null) {
            body.put("email", email);
        }
        return idOf(call(owner, HttpMethod.POST, "/contacts", body, Map.class));
    }

    private Map<String, Object> standing(Account account, long contact) {
        return call(account, HttpMethod.GET, "/network/invitations/contacts/" + contact, null, Map.class).getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void invitesAContactOnceNeverOneWhoDeclinedAndTellsWhenOneJoins() throws Exception {
        flags.set(FeatureFlag.SOCIAL_NETWORK, true);
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-1");
        join(alice);
        String carolEmail = "carol." + UUID.randomUUID() + "@example.org";
        long carol = contact(alice, "Carol", carolEmail);
        long nobody = contact(alice, "Sans", null);
        long bobContact = contact(alice, "Bob", bobEmail);
        assertThat(standing(alice, nobody)).containsEntry("standing", "NO_EMAIL");
        assertThat(standing(alice, bobContact)).containsEntry("standing", "INVITABLE");
        String bobHandle = join(bob);
        assertThat(standing(alice, bobContact)).containsEntry("standing", "MEMBER").containsEntry("handle", bobHandle);
        assertThat(call(bob, HttpMethod.GET, "/network/invitations/contacts/" + carol, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // Alice invites Carol, once.
        assertThat(call(alice, HttpMethod.POST, "/network/invitations/contacts/" + carol, null, Map.class).getBody())
            .containsEntry("standing", "INVITED");
        assertThat(call(alice, HttpMethod.POST, "/network/invitations/contacts/" + carol, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(alice, HttpMethod.POST, "/network/invitations/contacts/" + bobContact, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer).send(sent.capture());
        Email email = sent.getValue();
        assertThat(email.to()).isEqualTo(carolEmail);
        assertThat(email.subject()).contains("alice");
        assertThat(email.text()).contains("Carol,").contains("/network-invitation#");
        assertThat(email.headers()).containsKey("List-Unsubscribe");
        String token = email.text().replaceAll("(?s).*/network-invitation#([A-Za-z0-9_-]+).*", "$1");

        // Carol reads it without an account, and declines every invitation.
        assertThat(exchange("/public/network-invitations/" + token, HttpMethod.GET, null, Map.class).getBody()).containsEntry("declined", false);
        assertThat(post("/public/network-invitations/" + token + "/decline", null).getBody()).containsEntry("declined", true);
        assertThat(standing(alice, carol)).containsEntry("standing", "DECLINED");
        long bobsCarol = contact(bob, "Carol", carolEmail.toUpperCase());
        assertThat(standing(bob, bobsCarol)).containsEntry("standing", "DECLINED");
        assertThat(call(bob, HttpMethod.POST, "/network/invitations/contacts/" + bobsCarol, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(exchange("/public/network-invitations/unknown", HttpMethod.GET, null, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Erin, invited, creates her account and joins: Alice is told.
        String erinEmail = "erin." + UUID.randomUUID() + "@example.org";
        long erin = contact(alice, "Erin", erinEmail);
        call(alice, HttpMethod.POST, "/network/invitations/contacts/" + erin, null, Map.class);
        Account erinAccount = registerAs(erinEmail);
        String erinHandle = join(erinAccount);
        List<Map<String, Object>> notices = call(alice, HttpMethod.GET, "/network/notifications", null, List.class).getBody();
        assertThat(notices).anySatisfy(notice -> {
            assertThat(notice).containsEntry("type", "INVITE_JOINED");
            assertThat((List<Map<String, Object>>) notice.get("actors")).singleElement()
                .satisfies(actor -> assertThat(actor).containsEntry("handle", erinHandle));
        });
        assertThat(standing(alice, erin)).containsEntry("standing", "MEMBER");
    }
}
