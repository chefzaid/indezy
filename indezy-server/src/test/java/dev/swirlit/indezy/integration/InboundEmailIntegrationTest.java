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

    @MockitoBean
    private dev.swirlit.indezy.ai.ReplySortingAiService sorting;

    @MockitoBean
    private dev.swirlit.indezy.ai.AiService ai;

    @MockitoBean
    private dev.swirlit.indezy.ai.InboxReadingAiService inboxReading;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private dev.swirlit.indezy.service.InterviewStepService steps;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private dev.swirlit.indezy.service.ProjectService projectService;

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

        // Alice's AI sorts the answer: it asks not to be written to again.
        when(ai.status(any())).thenReturn(new dev.swirlit.indezy.ai.AiDtos.Status(true, null, false, null, Map.of(), 0, 10, Map.of()));
        when(sorting.sort(any(), any(), any(), any())).thenReturn(new dev.swirlit.indezy.ai.ReplySortingAiService.Sort(
            dev.swirlit.indezy.model.enums.ReplyKind.DO_NOT_CONTACT, null));
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
        assertThat(exchanges).anySatisfy(item -> assertThat(item).containsEntry("direction", "INCOMING").containsEntry("summary", "Re: Mission Java")
            .containsEntry("replyKind", "DO_NOT_CONTACT"));
        assertThat(call(alice, HttpMethod.GET, "/contacts/" + contact, null, Map.class).getBody()).containsEntry("doNotContact", true);
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

    /** Sends Alice's email to a new contact of her client and answers it through the webhook, sorted as {@code sort}. */
    @SuppressWarnings("unchecked")
    private long answered(String email, String emailId, dev.swirlit.indezy.ai.ReplySortingAiService.Sort sort) throws Exception {
        long contact = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Marc", "lastName", "Durand", "email",
            email, "clientId", aliceClientId), Map.class));
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenReturn("e-" + emailId);
        long exchangeId = ((Number) call(alice, HttpMethod.POST, "/contacts/" + contact + "/emails", Map.of("subject", "Disponible en janvier",
            "body", "Bonjour Marc,", "language", "FR"), Map.class).getBody().get("id")).longValue();
        String token = jdbcTemplate.queryForObject("SELECT reply_token FROM contact_interactions WHERE id = ?", String.class, exchangeId);
        when(ai.status(any())).thenReturn(new dev.swirlit.indezy.ai.AiDtos.Status(true, null, false, null, Map.of(), 0, 10, Map.of()));
        when(sorting.sort(any(), any(), any(), any())).thenReturn(sort);
        when(mailer.received(emailId)).thenReturn(new ResendMailer.Received(email, "RE: Disponible en janvier", "Bonjour."));
        String body = "{\"type\":\"email.received\",\"data\":{\"email_id\":\"" + emailId + "\",\"from\":\"" + email +
            "\",\"to\":[\"reply+" + token + "@replies.example\"],\"subject\":\"RE: Disponible en janvier\"}}";
        assertThat(deliver("msg_" + emailId, body, true).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return contact;
    }

    @Test
    @SuppressWarnings("unchecked")
    void anInterestedAnswerOpensAnOpportunityWithBothEmailsInItsJournal() throws Exception {
        jdbcTemplate.update("UPDATE users SET target_daily_rate = 650 WHERE email = ?", aliceEmail);
        long contact = answered("marc@acme.example", "in-10",
            new dev.swirlit.indezy.ai.ReplySortingAiService.Sort(dev.swirlit.indezy.model.enums.ReplyKind.INTERESTED, null));

        List<Map<String, Object>> exchanges = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/contacts/" + contact + "/interactions",
            null, Map.class).getBody().get("interactions");
        assertThat(exchanges).hasSize(2).allSatisfy(item -> assertThat(item).containsEntry("projectRole", "Disponible en janvier"));
        Number projectId = (Number) exchanges.getFirst().get("projectId");
        Map<String, Object> project = call(alice, HttpMethod.GET, "/projects/" + projectId, null, Map.class).getBody();
        assertThat(project).containsEntry("status", "CONTACT").containsEntry("dailyRate", 650)
            .containsEntry("clientId", aliceClientId.intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aNotNowAnswerComesDueOnTheDayItGave() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris"));
        long contact = answered("paul@acme.example", "in-20",
            new dev.swirlit.indezy.ai.ReplySortingAiService.Sort(dev.swirlit.indezy.model.enums.ReplyKind.NOT_NOW, today));

        List<Map<String, Object>> due = (List<Map<String, Object>>) call(alice, HttpMethod.GET, "/projects/stats/dashboard/" + alice.freelanceId(),
            null, Map.class).getBody().get("followUpsDue");
        assertThat(due).anySatisfy(item -> assertThat(item).containsEntry("contactId", (int) contact)
            .containsEntry("writeBackOn", today.toString()));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM projects WHERE freelance_id = ?", Long.class, alice.freelanceId()))
            .isZero();
    }

    /** Alice's inbox watcher address, asked for, and an email from {@code sender} forwarded to it. */
    @SuppressWarnings("unchecked")
    private String forward(String emailId, String sender, String subject, dev.swirlit.indezy.ai.InboxReadingAiService.Read read) throws Exception {
        String address = (String) call(alice, HttpMethod.POST, "/ai/inbox-watcher/address", null, Map.class).getBody().get("address");
        when(ai.status(any())).thenReturn(new dev.swirlit.indezy.ai.AiDtos.Status(true, null, false, null, Map.of(), 0, 10, Map.of()));
        when(inboxReading.read(any(), any(), any(), any(), any())).thenReturn(read);
        when(mailer.received(emailId)).thenReturn(new ResendMailer.Received(sender, subject, "Bonjour Alice,"));
        String body = "{\"type\":\"email.received\",\"data\":{\"email_id\":\"" + emailId + "\",\"from\":\"" + sender +
            "\",\"to\":[\"" + address + "\"],\"subject\":\"" + subject + "\"}}";
        assertThat(deliver("msg_" + emailId, body, true).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        // Delivered again: handled once.
        assertThat(deliver("msg_" + emailId + "b", body, true).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        return address;
    }

    @Test
    @SuppressWarnings("unchecked")
    void anInterviewInviteForwardedAddsTheStepMovesTheOpportunityAndDraftsTheAnswer() throws Exception {
        long contact = idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Inès", "lastName", "Roy", "email",
            "ines@acme.example", "clientId", aliceClientId), Map.class));
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "status", "CONTACT",
            "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));
        java.time.LocalDateTime slot = java.time.LocalDateTime.now().plusDays(3).withHour(10).withMinute(0).withSecond(0).withNano(0);
        String address = forward("in-30", "Inès Roy <ines@acme.example>", "Entretien Lead Java", new dev.swirlit.indezy.ai.InboxReadingAiService.Read(
            dev.swirlit.indezy.model.enums.InboxEmailKind.INTERVIEW_INVITE, List.of(slot), null, "Bonjour Inès, jeudi 10 h me convient."));
        assertThat(address).matches("inbox\\+[0-9a-f]{40}@replies\\.example");

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM projects WHERE id = ?", String.class, project)).isEqualTo("INTERVIEW");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM interview_steps WHERE project_id = ? AND status = 'PLANNED'", Long.class, project))
            .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT body FROM autopilot_drafts WHERE contact_id = ?", String.class, contact))
            .contains("jeudi 10 h");
        assertThat(jdbcTemplate.queryForObject("SELECT answer FROM autopilot_drafts WHERE contact_id = ?", Boolean.class, contact)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contact_interactions WHERE contact_id = ? AND direction = 'INCOMING'",
            Long.class, contact)).isEqualTo(1L);
        Map<String, Object> watcher = call(alice, HttpMethod.GET, "/ai/inbox-watcher", null, Map.class).getBody();
        assertThat(watcher).containsEntry("available", true).containsEntry("address", address);
        assertThat((List<Map<String, Object>>) watcher.get("recent")).singleElement().satisfies(item -> assertThat(item)
            .containsEntry("kind", "INTERVIEW_INVITE").containsEntry("action", "INTERVIEW_STEP").containsEntry("projectId", (int) project)
            .containsEntry("sender", "ines@acme.example"));
        // Bob sees none of it.
        assertThat((List<?>) call(bob, HttpMethod.GET, "/ai/inbox-watcher", null, Map.class).getBody().get("recent")).isEmpty();
    }

    @Test
    void aRejectionForwardedAbandonsTheOpportunityAndAnAskFromSomeoneNewAddsThemAndDraftsTheAnswer() throws Exception {
        idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Hugo", "lastName", "Petit", "email",
            "hugo@acme.example", "clientId", aliceClientId), Map.class));
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Data", "dailyRate", 600, "status", "INTERVIEW",
            "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));
        forward("in-40", "hugo@acme.example", "Votre candidature", new dev.swirlit.indezy.ai.InboxReadingAiService.Read(
            dev.swirlit.indezy.model.enums.InboxEmailKind.REJECTION, List.of(), dev.swirlit.indezy.model.enums.LostReason.POSITION_FILLED, null));
        assertThat(jdbcTemplate.queryForMap("SELECT status, lost_reason, refused_by FROM projects WHERE id = ?", project))
            .containsEntry("STATUS", "LOST").containsEntry("LOST_REASON", "POSITION_FILLED").containsEntry("REFUSED_BY", "RECRUITER");

        forward("in-41", "Recrutement <jobs@acme.example>", "Dispo ?", new dev.swirlit.indezy.ai.InboxReadingAiService.Read(
            dev.swirlit.indezy.model.enums.InboxEmailKind.AVAILABILITY_ASK, List.of(), null, "Bonjour, disponible le 5 janvier."));
        Map<String, Object> added = jdbcTemplate.queryForMap("SELECT id, client_id FROM contacts WHERE email = 'jobs@acme.example'");
        assertThat(((Number) added.get("CLIENT_ID")).longValue()).isEqualTo(aliceClientId);
        assertThat(jdbcTemplate.queryForObject("SELECT subject FROM autopilot_drafts WHERE contact_id = ?", String.class, added.get("ID")))
            .isEqualTo("Re: Dispo ?");

        // From a webmail, someone unknown is not added (their domain says nothing of their company); the envelope's sender
        // stands in for an empty header.
        idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Léa", "lastName", "Gmail", "email", "lea.g@gmail.com",
            "clientId", aliceClientId), Map.class));
        when(mailer.received("in-42")).thenReturn(new ResendMailer.Received("", "Dispo ?", "Bonjour."));
        String body = "{\"type\":\"email.received\",\"data\":{\"email_id\":\"in-42\",\"from\":\"paul.esn@gmail.com\",\"to\":[\"" +
            call(alice, HttpMethod.GET, "/ai/inbox-watcher", null, Map.class).getBody().get("address") + "\"]}}";
        assertThat(deliver("msg_in-42", body, true).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contacts WHERE email = 'paul.esn@gmail.com'", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT sender FROM inbox_emails WHERE email_id = 'in-42'", String.class))
            .isEqualTo("paul.esn@gmail.com");

        // From a company not in the workspace yet: added with it, named from the domain.
        when(mailer.received("in-43")).thenReturn(new ResendMailer.Received("Inès <ines@newesn.example>", "Dispo ?", "Bonjour."));
        String fromNew = body.replace("in-42", "in-43").replace("paul.esn@gmail.com", "ines@newesn.example");
        assertThat(deliver("msg_in-43", fromNew, true).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbcTemplate.queryForObject("SELECT c.company_name FROM contacts p JOIN clients c ON c.id = p.client_id " +
            "WHERE p.email = 'ines@newesn.example'", String.class)).isEqualTo("Newesn");
    }

    @Test
    void sendsGmailsForwardingConfirmationOnToTheMember() throws Exception {
        when(mailer.send(any())).thenReturn("e-50");
        forward("in-50", "forwarding-noreply@google.com", "Gmail Forwarding Confirmation", new dev.swirlit.indezy.ai.InboxReadingAiService.Read(
            dev.swirlit.indezy.model.enums.InboxEmailKind.OTHER, List.of(), null, null));
        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo(aliceEmail);
        verify(inboxReading, org.mockito.Mockito.never()).read(any(), any(), any(), any(), any());
    }

    @Test
    void anActionThatFailsIsUndoneAloneAndTheEmailOrAnswerStaysRecorded() throws Exception {
        long project = idOf(call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650, "status", "CONTACT",
            "clientId", aliceClientId, "freelanceId", alice.freelanceId()), Map.class));
        idOf(call(alice, HttpMethod.POST, "/contacts", Map.of("firstName", "Inès", "lastName", "Roy", "email", "ines@acme.example",
            "clientId", aliceClientId), Map.class));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("step refused")).when(steps).create(any());
        forward("in-60", "ines@acme.example", "Entretien", new dev.swirlit.indezy.ai.InboxReadingAiService.Read(
            dev.swirlit.indezy.model.enums.InboxEmailKind.INTERVIEW_INVITE, List.of(), null, "Bonjour Inès,"));
        assertThat(jdbcTemplate.queryForMap("SELECT kind, action FROM inbox_emails WHERE email_id = 'in-60'"))
            .containsEntry("KIND", "INTERVIEW_INVITE").containsEntry("ACTION", "NONE");
        // Undone together: no exchange, no draft, the opportunity where it was.
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM projects WHERE id = ?", String.class, project)).isEqualTo("CONTACT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contact_interactions WHERE inbound_email_id = 'in-60'", Long.class)).isZero();

        // An interested answer whose opportunity cannot be created stays recorded and sorted.
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("project refused")).when(projectService).create(any());
        long contact = answered("noe@acme.example", "in-61",
            new dev.swirlit.indezy.ai.ReplySortingAiService.Sort(dev.swirlit.indezy.model.enums.ReplyKind.INTERESTED, null));
        assertThat(jdbcTemplate.queryForObject("SELECT reply_kind FROM contact_interactions WHERE contact_id = ? AND direction = 'INCOMING'",
            String.class, contact)).isEqualTo("INTERESTED");
    }
}
