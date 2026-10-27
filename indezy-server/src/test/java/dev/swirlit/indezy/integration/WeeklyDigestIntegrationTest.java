package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.service.WeeklyDigestService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alice's weekly digest over HTTP, Resend replaced: turned on from her notifications, sent once a week in English with
 * her interview and the new offer matching her profile, given back when Resend refuses it, never sent to Bob, who left it
 * off, and stopped by the mailbox's one-click unsubscribe.
 */
@TestPropertySource(properties = "indezy.mail.resend-api-key=re_test")
class WeeklyDigestIntegrationTest extends OfferIntegrationTestSupport {

    @MockitoBean
    private ResendMailer mailer;

    @Autowired
    private WeeklyDigestService digests;

    @Test
    @SuppressWarnings("unchecked")
    void sendsAliceHerWeekOnceAndStopsFromTheEmail() throws Exception {
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenThrow(new MailException("Resend refused it")).thenReturn("49a3999c");
        users.findByEmail(alice.email()).ifPresent(user -> {
            user.setSkills(List.of("Java"));
            user.setLanguagePreference("en");
            users.save(user);
        });
        Map<String, Object> settings = new HashMap<>(call(alice, HttpMethod.GET, "/users/notifications", null).getBody());
        settings.put("weeklyReports", true);
        assertThat(call(alice, HttpMethod.PUT, "/users/notifications", settings).getBody()).containsEntry("weeklyReports", true);
        long clientId = ((Number) call(alice, HttpMethod.POST, "/clients", Map.of("companyName", "Acme", "city", "Lyon", "isFinal", true,
            "freelanceId", alice.freelanceId())).getBody().get("id")).longValue();
        long projectId = ((Number) call(alice, HttpMethod.POST, "/projects", Map.of("role", "Lead Java", "dailyRate", 650,
            "freelanceId", alice.freelanceId(), "clientId", clientId, "status", "INTERVIEW")).getBody().get("id")).longValue();
        assertThat(call(alice, HttpMethod.POST, "/interview-steps", Map.of("title", "Technical interview", "projectId", projectId,
            "status", "PLANNED", "date", LocalDateTime.now().plusDays(2).withNano(0).toString())).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        LocalDate week = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        // Refused by Resend: given back, and sent at the next run.
        assertThat(digests.send(week)).isZero();
        assertThat(digests.send(week)).isEqualTo(1);
        assertThat(digests.send(week)).isZero();

        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer, org.mockito.Mockito.times(2)).send(sent.capture());
        Email email = sent.getValue();
        assertThat(email.to()).isEqualTo(alice.email());
        assertThat(email.subject()).startsWith("Your Indezy week of");
        assertThat(email.text()).contains("Technical interview", "Lead Java (Acme)", "1 open opportunities", "1 new offer matches your profile",
            "Développeur Java Spring");
        assertThat(email.idempotencyKey()).isEqualTo("weekly-digest/" + users.findByEmail(alice.email()).orElseThrow().getId() + "/" + week);
        String unsubscribe = email.headers().get("List-Unsubscribe");
        assertThat(unsubscribe).contains("/api/public/weekly-digest/unsubscribe/");

        // The mailbox's one-click unsubscribe stops it.
        String token = unsubscribe.substring(unsubscribe.lastIndexOf('/') + 1, unsubscribe.length() - 1);
        assertThat(restTemplate.postForEntity(url("/public/weekly-digest/unsubscribe/" + token), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(call(alice, HttpMethod.GET, "/users/notifications", null).getBody()).containsEntry("weeklyReports", false);
        assertThat(restTemplate.postForEntity(url("/public/weekly-digest/unsubscribe/not-a-token"), null, String.class).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        clearInvocations(mailer);
        assertThat(digests.send(week.plusWeeks(1))).isZero();
        verify(mailer, never()).send(any());
    }
}
