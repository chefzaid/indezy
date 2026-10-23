package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.SavedOfferSearch;
import dev.swirlit.indezy.model.enums.SavedSearchEmails;
import dev.swirlit.indezy.service.SavedSearchEmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A saved search's emails over HTTP with two real accounts, Resend replaced: turned on in the app's language, the offers
 * found since sent to the workspace's address with its unsubscribe headers, never twice, given back when Resend
 * refuses them, stopped by the link without an account, and stopped after 60 days of emails left unopened.
 */
@TestPropertySource(properties = "indezy.mail.resend-api-key=re_test")
class SavedSearchEmailIntegrationTest extends OfferIntegrationTestSupport {

    @MockitoBean
    private ResendMailer mailer;

    @Autowired
    private SavedSearchEmailService emails;

    private long searchId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void saveASearch() throws Exception {
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("49a3999c");
        ResponseEntity<Map> saved = call(alice, HttpMethod.POST, "/offers/searches", Map.of("name", "Java", "filters", Map.of("q", "java")));
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        searchId = ((Number) saved.getBody().get("id")).longValue();
        assertThat(saved.getBody()).containsEntry("emailsAvailable", true);
        assertThat((Map<String, Object>) saved.getBody().get("emails")).containsEntry("frequency", "OFF");
    }

    @Test
    @SuppressWarnings("unchecked")
    void emailsTheOffersFoundSinceOnceToTheWorkspaceInItsLanguage() throws Exception {
        ResponseEntity<Map> turnedOn = call(alice, HttpMethod.PUT, "/offers/searches/" + searchId + "/emails",
            Map.of("frequency", "INSTANT", "language", "EN"));
        assertThat((Map<String, Object>) turnedOn.getBody().get("emails")).containsEntry("frequency", "INSTANT").containsEntry("language", "EN");
        // Turned on now: the offers found before are not sent, nor is anything before 30 minutes.
        assertThat(emails.sendDue()).isZero();
        sendable(Duration.ofMinutes(31));
        JobOffer found = offers.save(offer("fw-3", "Lead développeur Java", "Exemple Conseil", Instant.now().minus(Duration.ofMinutes(10)), 45.764, 4.8357));

        assertThat(emails.sendDue()).isEqualTo(1);
        assertThat(emails.sendDue()).isZero();

        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer).send(sent.capture());
        Email email = sent.getValue();
        String token = savedSearches.findById(searchId).orElseThrow().getEmailToken();
        assertThat(email.to()).isEqualTo(alice.email());
        assertThat(email.subject()).isEqualTo("1 new offer: Java");
        assertThat(email.text()).contains("Lead développeur Java").contains("/offers/" + found.getId() + "?search=" + searchId + "&from=email")
            .doesNotContain("Développeur Java Spring");
        assertThat(email.headers()).containsEntry("List-Unsubscribe", "<https://indezy.swirlit.dev/api/public/saved-search-emails/unsubscribe/" + token + ">");
        assertThat(token).hasSize(43);
        // Bob can neither change Alice's search nor tell that she opened it.
        assertThat(call(bob, HttpMethod.PUT, "/offers/searches/" + searchId + "/emails", Map.of("frequency", "OFF")).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(savedSearches.findById(searchId).orElseThrow().getUnopenedSince()).isNotNull();
        assertThat(call(alice, HttpMethod.POST, "/offers/searches/" + searchId + "/emails/opened", null).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(savedSearches.findById(searchId).orElseThrow().getUnopenedSince()).isNull();
    }

    @Test
    void givesTheOffersBackToTheNextEmailWhenResendRefusesThem() throws Exception {
        call(alice, HttpMethod.PUT, "/offers/searches/" + searchId + "/emails", Map.of("frequency", "INSTANT"));
        Instant until = sendable(Duration.ofMinutes(45));
        offers.save(offer("fw-3", "Lead développeur Java", null, Instant.now().minus(Duration.ofMinutes(10)), 45.764, 4.8357));
        when(mailer.send(any())).thenThrow(new MailException("Resend answered 429: rate_limit_exceeded"));

        assertThat(emails.sendDue()).isZero();

        assertThat(savedSearches.findById(searchId).orElseThrow().getEmailedUntil()).isEqualTo(until);
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopsByItsLinkWithoutAnAccount() {
        call(alice, HttpMethod.PUT, "/offers/searches/" + searchId + "/emails", Map.of("frequency", "DAILY", "hour", 7, "language", "FR"));
        String token = savedSearches.findById(searchId).orElseThrow().getEmailToken();

        ResponseEntity<Map> shown = linkCall(HttpMethod.GET, "/public/saved-search-emails", token);
        assertThat(shown.getBody()).containsEntry("searchName", "Java").containsEntry("frequency", "DAILY").containsEntry("language", "FR");
        // A mailbox's one-click unsubscribe, posted as RFC 8058 says.
        HttpHeaders form = new HttpHeaders();
        form.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> oneClick = restTemplate.exchange(url("/public/saved-search-emails/unsubscribe/" + token), HttpMethod.POST,
            new HttpEntity<>("List-Unsubscribe=One-Click", form), String.class);
        assertThat(oneClick.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(linkCall(HttpMethod.GET, "/public/saved-search-emails", token).getBody()).containsEntry("frequency", "OFF");
        assertThat(linkCall(HttpMethod.POST, "/public/saved-search-emails/unsubscribe", token).getBody()).containsEntry("frequency", "OFF");
        List<Map<String, Object>> listed = call(alice, HttpMethod.GET, "/offers/searches", null, List.class).getBody();
        assertThat((Map<String, Object>) listed.get(0).get("emails")).containsEntry("frequency", "OFF");
        assertThat(linkCall(HttpMethod.GET, "/public/saved-search-emails", "not-a-token").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(linkCall(HttpMethod.GET, "/public/saved-search-emails", "x".repeat(65)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopsAndSaysSoAfterSixtyDaysOfEmailsLeftUnopened() throws Exception {
        call(alice, HttpMethod.PUT, "/offers/searches/" + searchId + "/emails", Map.of("frequency", "INSTANT", "language", "FR"));
        sendable(Duration.ofMinutes(31));
        SavedOfferSearch search = savedSearches.findById(searchId).orElseThrow();
        search.setUnopenedSince(Instant.now().minus(Duration.ofDays(61)));
        savedSearches.save(search);
        clearInvocations(mailer);

        assertThat(emails.sendDue()).isEqualTo(1);

        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer, times(1)).send(sent.capture());
        assertThat(sent.getValue().subject()).isEqualTo("Emails arrêtés : Java");
        Map<String, Object> listed = (Map<String, Object>) call(alice, HttpMethod.GET, "/offers/searches", null, List.class).getBody().get(0);
        assertThat((Map<String, Object>) listed.get("emails")).containsEntry("frequency", "OFF").extractingByKey("stoppedAt").isNotNull();
        // Turned on again, it has 60 more days.
        call(alice, HttpMethod.PUT, "/offers/searches/" + searchId + "/emails", Map.of("frequency", "INSTANT"));
        SavedOfferSearch again = savedSearches.findById(searchId).orElseThrow();
        assertThat(again.getEmailsStoppedAt()).isNull();
        assertThat(again.getUnopenedSince()).isNull();
    }

    @Test
    void sendsNothingWithoutAResendKey() throws Exception {
        call(alice, HttpMethod.PUT, "/offers/searches/" + searchId + "/emails", Map.of("frequency", "INSTANT"));
        sendable(Duration.ofHours(1));
        when(mailer.configured()).thenReturn(false);

        assertThat(emails.sendDue()).isZero();

        verify(mailer, never()).send(any());
        assertThat(call(alice, HttpMethod.PUT, "/offers/searches/" + searchId + "/emails", Map.of("frequency", "DAILY", "hour", 24))
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** Moves the search's last email back by {@code ago}; the new start of its next email. */
    private Instant sendable(Duration ago) {
        SavedOfferSearch search = savedSearches.findById(searchId).orElseThrow();
        assertThat(search.getEmailFrequency()).isNotEqualTo(SavedSearchEmails.OFF);
        Instant until = Instant.now().minus(ago);
        search.setEmailedUntil(until);
        savedSearches.save(search);
        return savedSearches.findById(searchId).orElseThrow().getEmailedUntil();
    }

    private ResponseEntity<Map> linkCall(HttpMethod method, String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Email-Token", token);
        return restTemplate.exchange(url(path), method, new HttpEntity<>(headers), Map.class);
    }
}
