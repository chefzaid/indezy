package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.JobOfferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private JobOfferRepository offers;

    @Autowired
    private dev.swirlit.indezy.service.ContactEmailScheduleService schedule;

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

    @Test
    @SuppressWarnings("unchecked")
    void listsTheEsnContactsWorkedWithAndAttachesTheDossier() throws Exception {
        long esn = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Conseil Exemple", "city", "Lyon", "isFinal", false, "freelanceId", alice.freelanceId()), Map.class));
        long endClient = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Banque Exemple", "city", "Lyon", "isFinal", true, "freelanceId", alice.freelanceId()), Map.class));
        long unknown = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Autre ESN", "city", "Lyon", "isFinal", false, "freelanceId", alice.freelanceId()), Map.class));
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "status", "WON", "clientId", endClient,
            "middlemanId", esn), Map.class);
        long lea = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Léa", "lastName", "Martin", "email", "lea@conseil.example",
            "clientId", esn), Map.class));
        call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Paul", "lastName", "Sans", "clientId", esn), Map.class);
        call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Zoé", "lastName", "Banque", "email", "zoe@banque.example",
            "clientId", endClient), Map.class);
        call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Marc", "lastName", "Autre", "email", "marc@autre.example",
            "clientId", unknown), Map.class);
        // An ESN never worked with whose contact answered before counts, all its contacts; one written to last week is left out for now.
        long ines = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Inès", "lastName", "Répond", "email", "ines@autre.example",
            "clientId", unknown), Map.class));
        call(alice, HttpMethod.POST, "/contacts/" + ines + "/interactions", Map.of("occurredOn", java.time.LocalDate.now().minusDays(60).toString(),
            "channel", "EMAIL", "direction", "INCOMING", "summary", "Bonjour"), Map.class);
        long recent = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Rémi", "lastName", "Récent", "email",
            "remi@conseil.example", "clientId", esn), Map.class));
        call(alice, HttpMethod.POST, "/contacts/" + recent + "/interactions", Map.of("occurredOn", java.time.LocalDate.now().minusDays(7).toString(),
            "channel", "EMAIL", "direction", "OUTGOING", "summary", "Point"), Map.class);

        List<Map<String, Object>> recipients = call(alice, HttpMethod.GET, "/contacts/emails/availability-recipients", null, List.class).getBody();
        assertThat(recipients).extracting(recipient -> recipient.get("name") + " <" + recipient.get("email") + "> " + recipient.get("company"))
            .containsExactly("Inès Répond <ines@autre.example> Autre ESN", "Léa Martin <lea@conseil.example> Conseil Exemple",
                "Marc Autre <marc@autre.example> Autre ESN");
        assertThat(recipients.getFirst()).containsEntry("answers", 1);
        // Léa's ESN paid 650 a day on a mission won through it, which ranks her before Marc.
        assertThat(recipients.get(1)).containsEntry("ratePaid", 650);
        assertThat(call(bob, HttpMethod.GET, "/contacts/emails/availability-recipients", null, List.class).getBody()).isEmpty();

        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-3");
        call(alice, HttpMethod.POST, "/contacts/" + lea + "/emails", Map.of("subject", "Disponible en février", "body", "Bonjour Léa,",
            "dossier", true, "language", "FR"), Map.class);
        ArgumentCaptor<Email> emails = ArgumentCaptor.forClass(Email.class);
        verify(mailer).send(emails.capture());
        assertThat(emails.getValue().attachments()).singleElement().satisfies(file -> {
            assertThat(file.contentType()).isEqualTo("application/pdf");
            assertThat(file.fileName()).endsWith(".pdf");
            assertThat(new String(file.content(), 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void addsTheEsnsPostingMissionsForTheProfileAndLeavesOutWhoeverAskedNotToBeContacted() {
        Map<String, Object> profile = call(alice, HttpMethod.GET, "/users/profile", null, Map.class).getBody();
        profile.put("skills", List.of("Java", "Kafka"));
        call(alice, HttpMethod.PUT, "/users/profile", profile, Map.class);
        long hiring = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Nouvelle ESN", "city", "Lyon", "isFinal", false,
            "freelanceId", alice.freelanceId()), Map.class));
        long other = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "ESN Python", "city", "Lyon", "isFinal", false,
            "freelanceId", alice.freelanceId()), Map.class));
        long sam = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Sam", "lastName", "Recrute", "email", "sam@nouvelle.example",
            "clientId", hiring), Map.class));
        long noe = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Noé", "lastName", "Refuse", "email", "noe@nouvelle.example",
            "clientId", hiring, "doNotContact", true), Map.class));
        call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Ana", "lastName", "Python", "email", "ana@python.example",
            "clientId", other), Map.class);
        java.time.Instant now = java.time.Instant.now();
        List<JobOffer> saved = offers.saveAll(List.of(offer("NOUVELLE-esn", List.of("Java"), now.minus(java.time.Duration.ofDays(3))),
            offer("Nouvelle ESN", List.of("Kafka", "Java"), now.minus(java.time.Duration.ofDays(20))),
            offer("Nouvelle ESN", List.of("Java"), now.minus(java.time.Duration.ofDays(80))),
            offer("ESN Python", List.of("Python"), now.minus(java.time.Duration.ofDays(2)))));
        try {
            List<Map<String, Object>> recipients = call(alice, HttpMethod.GET, "/contacts/emails/availability-recipients", null, List.class).getBody();
            assertThat(recipients).extracting(recipient -> recipient.get("name") + " " + recipient.get("postings"))
                .containsExactly("Sam Recrute 2");
            assertThat(recipients.getFirst()).containsEntry("mission", "Développeur Java");
            assertThat(call(alice, HttpMethod.GET, "/contacts/" + noe, null, Map.class).getBody()).containsEntry("doNotContact", true);

            Map<String, Object> contact = call(alice, HttpMethod.GET, "/contacts/" + noe, null, Map.class).getBody();
            contact.put("doNotContact", false);
            call(alice, HttpMethod.PUT, "/contacts/" + noe, contact, Map.class);
            assertThat(call(alice, HttpMethod.GET, "/contacts/emails/availability-recipients", null, List.class).getBody())
                .extracting(recipient -> ((Map<String, Object>) recipient).get("name")).containsExactly("Noé Refuse", "Sam Recrute");
            assertThat(sam).isPositive();
        } finally {
            offers.deleteAll(saved);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsThePlannedEmailsWhenTheirTimeComesUnlessTheContactWasInTouchSince() throws Exception {
        long lea = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Léa", "lastName", "Plan", "email", "lea@plan.example",
            "clientId", aliceClientId), Map.class));
        long tom = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Tom", "lastName", "Plan", "email", "tom@plan.example",
            "clientId", aliceClientId), Map.class));
        long ana = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Ana", "lastName", "Plan", "email", "ana@plan.example",
            "clientId", aliceClientId), Map.class));
        Map<String, Object> plan = Map.of("emails", List.of(Map.of("contactId", lea, "subject", "Disponible", "body", "Bonjour Léa"),
            Map.of("contactId", tom, "subject", "Disponible", "body", "Bonjour Tom"), Map.of("contactId", ana, "subject", "Disponible", "body", "Bonjour Ana")),
            "dossier", false, "language", "FR", "days", 2, "dailyMax", 10);

        assertThat(call(bob, HttpMethod.POST, "/contacts/emails/scheduled", plan, String.class).getStatusCode())
            .isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
        List<Map<String, Object>> planned = call(alice, HttpMethod.POST, "/contacts/emails/scheduled", plan, List.class).getBody();
        assertThat(planned).extracting(email -> email.get("name")).containsExactly("Léa Plan", "Tom Plan", "Ana Plan");
        assertThat(call(alice, HttpMethod.GET, "/contacts/emails/scheduled", null, List.class).getBody()).hasSize(3);
        assertThat(call(bob, HttpMethod.GET, "/contacts/emails/scheduled", null, List.class).getBody()).isEmpty();
        Object anaEmail = planned.get(2).get("id");
        assertThat(call(bob, HttpMethod.DELETE, "/contacts/emails/scheduled/" + anaEmail, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(alice, HttpMethod.DELETE, "/contacts/emails/scheduled/" + anaEmail, null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(alice, HttpMethod.POST, "/contacts/emails/scheduled", Map.of("emails", List.of(), "days", 2, "dailyMax", 10), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Tom answered meanwhile: his email is dropped; Léa's goes, recorded in her exchanges.
        call(alice, HttpMethod.POST, "/contacts/" + tom + "/interactions", Map.of("occurredOn", java.time.LocalDate.now().toString(),
            "channel", "PHONE", "direction", "INCOMING", "summary", "Appel"), Map.class);
        jdbcTemplate.update("UPDATE scheduled_contact_emails SET send_at = ? WHERE contact_id IN (?, ?)",
            java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), lea, tom);
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-9");
        schedule.sendDue();

        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer, times(1)).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo("lea@plan.example");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM contact_interactions WHERE contact_id = ? AND email_sent_at IS NOT NULL",
            Integer.class, lea)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM contact_interactions WHERE contact_id = ? AND email_sent_at IS NOT NULL",
            Integer.class, tom)).isZero();
        assertThat(call(alice, HttpMethod.GET, "/contacts/emails/scheduled", null, List.class).getBody()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsWhatCameOfACampaign() throws Exception {
        long lea = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Léa", "lastName", "Campagne", "email", "lea@campagne.example",
            "clientId", aliceClientId), Map.class));
        Map<String, Object> started = call(alice, HttpMethod.POST, "/contacts/emails/campaigns", Map.of("name", "Disponible en janvier"), Map.class)
            .getBody();
        Object campaign = started.get("id");
        assertThat(started).containsEntry("name", "Disponible en janvier");
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-10");
        Map<String, Object> message = new java.util.HashMap<>(Map.of("subject", "Disponible", "body", "Bonjour Léa", "campaignId", campaign));
        assertThat(call(bob, HttpMethod.POST, "/contacts/emails/campaigns", Map.of("name", "x"), Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(alice, HttpMethod.POST, "/contacts/" + lea + "/emails", message, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Waiting for nothing and sent nothing, Bob's campaign is not listed.
        assertThat(call(bob, HttpMethod.GET, "/contacts/emails/campaigns", null, List.class).getBody()).isEmpty();

        // Léa answers, then an opportunity opens with her company and is won.
        call(alice, HttpMethod.POST, "/contacts/" + lea + "/interactions", Map.of("occurredOn", java.time.LocalDate.now().toString(),
            "channel", "EMAIL", "direction", "INCOMING", "summary", "Intéressée"), Map.class);
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "status", "CONTACT",
            "clientId", aliceClientId), Map.class));
        List<Map<String, Object>> results = call(alice, HttpMethod.GET, "/contacts/emails/campaigns", null, List.class).getBody();
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).containsEntry("sent", 1).containsEntry("answered", 1).containsEntry("opportunities", 1).containsEntry("won", 0);
        assertThat(results.getFirst().get("contactIds")).isEqualTo(List.of((int) lea));
        Map<String, Object> opportunity = call(alice, HttpMethod.GET, "/projects/" + project, null, Map.class).getBody();
        opportunity.put("status", "WON");
        call(alice, HttpMethod.PUT, "/projects/" + project, opportunity, Map.class);
        assertThat(((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/contacts/emails/campaigns", null, List.class).getBody()).getFirst())
            .containsEntry("won", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void letsTheContactOfACampaignObjectToBeingContactedAgain() throws Exception {
        long esn = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Objection ESN", "city", "Lyon", "isFinal", false,
            "freelanceId", alice.freelanceId()), Map.class));
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Java", "dailyRate", 600, "status", "WON", "clientId", esn), Map.class);
        long tom = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Tom", "lastName", "Objecte", "email", "tom@objection.example",
            "clientId", esn), Map.class));
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-30");
        Object campaign = call(alice, HttpMethod.POST, "/contacts/emails/campaigns", Map.of("name", "Disponible"), Map.class).getBody().get("id");
        call(alice, HttpMethod.POST, "/contacts/" + tom + "/emails", Map.of("subject", "Disponible", "body", "Bonjour Tom", "campaignId", campaign),
            Map.class);
        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer, org.mockito.Mockito.atLeastOnce()).send(sent.capture());
        Email email = sent.getAllValues().getLast();
        java.util.regex.Matcher link = java.util.regex.Pattern.compile("/api/public/emails/object/([A-Za-z0-9_-]{43})").matcher(email.text());
        assertThat(link.find()).as(email.text()).isTrue();
        assertThat(email.text()).contains("Pour ne plus être contacté par");
        String token = link.group(1);

        // The link's page asks first, changing nothing; confirming marks the contact, who leaves the list of recipients.
        ResponseEntity<String> page = exchange("/public/emails/object/" + token, HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page.getBody()).contains("<form method=\"post\"", "Ne plus recevoir");
        assertThat(call(alice, HttpMethod.GET, "/contacts/" + tom, null, Map.class).getBody().get("doNotContact")).isNotEqualTo(true);
        assertThat(exchange("/public/emails/object/" + token, HttpMethod.POST, HttpEntity.EMPTY, String.class).getBody())
            .contains("Vous ne serez plus contacté");
        assertThat(call(alice, HttpMethod.GET, "/contacts/" + tom, null, Map.class).getBody()).containsEntry("doNotContact", true);
        assertThat(exchange("/public/emails/object/" + token, HttpMethod.POST, HttpEntity.EMPTY, String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM contact_interactions WHERE contact_id = ? AND direction = 'INCOMING'",
            Integer.class, tom)).isEqualTo(1);
        assertThat((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/contacts/emails/availability-recipients", null, List.class).getBody())
            .noneMatch(recipient -> Long.valueOf(tom).equals(((Number) recipient.get("id")).longValue()));
        assertThat(exchange("/public/emails/object/" + "x".repeat(43), HttpMethod.GET, HttpEntity.EMPTY, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange("/public/emails/object/nope", HttpMethod.POST, HttpEntity.EMPTY, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // An email outside a campaign carries no such link.
        call(alice, HttpMethod.POST, "/contacts/" + tom + "/emails", Map.of("subject", "Point", "body", "Bonjour Tom"), Map.class);
        verify(mailer, org.mockito.Mockito.atLeast(2)).send(sent.capture());
        assertThat(sent.getAllValues().getLast().text()).doesNotContain("/object/");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ranksTheNextProspectionByWhatTheEarlierCampaignsGot() throws Exception {
        long silentEsn = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Silence ESN", "city", "Lyon", "isFinal", false,
            "freelanceId", alice.freelanceId()), Map.class));
        long answeringEsn = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Réponse ESN", "city", "Lyon", "isFinal", false,
            "freelanceId", alice.freelanceId()), Map.class));
        long plainEsn = idOf(call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Neutre ESN", "city", "Lyon", "isFinal", false,
            "freelanceId", alice.freelanceId()), Map.class));
        long old = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Java", "dailyRate", 600, "status", "WON", "clientId", silentEsn), Map.class));
        long older = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Java", "dailyRate", 600, "status", "WON", "clientId", plainEsn), Map.class));
        jdbcTemplate.update("UPDATE projects SET created_at = ? WHERE id IN (?, ?)", java.time.LocalDateTime.now().minusDays(100), old, older);
        long silent = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Sil", "lastName", "Rangé", "email", "sil@silence.example",
            "clientId", silentEsn), Map.class));
        long chosen = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Cho", "lastName", "Rangé", "email", "cho@reponse.example",
            "clientId", answeringEsn), Map.class));
        call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Neu", "lastName", "Rangé", "email", "neu@neutre.example", "clientId", plainEsn),
            Map.class);
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-20");
        for (String name : List.of("Septembre", "Octobre")) {
            Object campaign = call(alice, HttpMethod.POST, "/contacts/emails/campaigns", Map.of("name", name), Map.class).getBody().get("id");
            call(alice, HttpMethod.POST, "/contacts/" + silent + "/emails", Map.of("subject", name, "body", "Bonjour", "campaignId", campaign), Map.class);
            if ("Septembre".equals(name)) {
                call(alice, HttpMethod.POST, "/contacts/" + chosen + "/emails", Map.of("subject", name, "body", "Bonjour", "campaignId", campaign),
                    Map.class);
            }
        }
        // Written two months ago; an opportunity opened since with the second ESN only.
        jdbcTemplate.update("UPDATE contact_interactions SET occurred_on = ? WHERE contact_id IN (?, ?)", java.time.LocalDate.now().minusDays(60),
            silent, chosen);
        call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "status", "CONTACT", "clientId", answeringEsn), Map.class);

        List<Map<String, Object>> recipients = call(alice, HttpMethod.GET, "/contacts/emails/availability-recipients", null, List.class).getBody();
        assertThat(recipients).filteredOn(recipient -> "Rangé".equals(String.valueOf(recipient.get("name")).split(" ")[1]))
            .extracting(recipient -> recipient.get("company") + " " + recipient.get("campaignOpportunities") + " " + recipient.get("silentCampaigns"))
            .containsExactly("Réponse ESN 1 1", "Neutre ESN 0 0", "Silence ESN 0 2");
        // The best daily rate each company paid on a mission won through it.
        assertThat(recipients).filteredOn(recipient -> "Rangé".equals(String.valueOf(recipient.get("name")).split(" ")[1]))
            .extracting(recipient -> recipient.get("company") + " " + recipient.get("ratePaid"))
            .containsExactly("Réponse ESN null", "Neutre ESN 600", "Silence ESN 600");
    }

    private static JobOffer offer(String company, List<String> skills, java.time.Instant publishedAt) {
        JobOffer offer = new JobOffer();
        offer.setSite(OfferSite.FREE_WORK);
        offer.setExternalId("prospect-" + java.util.UUID.randomUUID());
        offer.setUrl("https://www.free-work.com/fr/tech-it/job-mission/" + offer.getExternalId());
        offer.setTitle("Développeur " + skills.getFirst());
        offer.setCompany(company);
        offer.setSkills(skills);
        offer.setSearchText("developpeur");
        offer.setPublishedAt(publishedAt);
        offer.setDatePrecision(OfferDatePrecision.EXACT);
        offer.setFirstSeenAt(publishedAt);
        offer.setLastSeenAt(publishedAt);
        return offer;
    }
}
