package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.service.BookingNoticeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
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
 * Alice hears of each call a recruiter books through her link, Resend replaced: one email, sent once, given back when
 * Resend refuses it; none once she turns these emails off, and none for a call cancelled before it was told. Her webhook
 * listening to booked calls is told too.
 */
@TestPropertySource(properties = "indezy.mail.resend-api-key=re_test")
class BookingNoticeIntegrationTest extends TwoAccountsIntegrationTest {

    @MockitoBean
    private ResendMailer mailer;

    @Autowired
    private BookingNoticeService notices;

    @Test
    @SuppressWarnings("unchecked")
    void emailsAliceEachCallBookedOnce() throws Exception {
        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenThrow(new MailException("Resend refused it")).thenReturn("49a3999c");
        Map<String, Object> page = call(alice, HttpMethod.PUT, "/users/booking-page", Map.of("enabled", true, "title", "Intro call",
            "durationMinutes", 30, "bufferMinutes", 0, "minNoticeHours", 0, "maxDaysAhead", 14, "windows",
            List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY").stream()
                .map(day -> Map.of("dayOfWeek", day, "start", "09:00", "end", "12:00")).toList()), Map.class).getBody();
        String slug = (String) page.get("slug");
        List<String> starts = starts(slug);
        call(alice, HttpMethod.POST, "/users/webhooks", Map.of("url", "http://127.0.0.1:9/hook", "events", List.of("CALL_BOOKED")), Map.class);

        book(slug, starts.get(starts.size() - 1), "Capgemini", "About the Java mission\nNext week");
        // Her webhook is told of the call, without the recruiter's name or email.
        String payload = jdbcTemplate.queryForObject("SELECT payload FROM webhook_deliveries WHERE event = 'CALL_BOOKED'", String.class);
        assertThat(payload).contains("\"company\":\"Capgemini\"", "\"startsAt\":\"" + starts.get(starts.size() - 1) + "\"")
            .doesNotContain("Jean", "jean@");
        assertThat(notices.sendDue()).isZero();
        assertThat(notices.sendDue()).isEqualTo(1);
        assertThat(notices.sendDue()).isZero();
        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer, times(2)).send(sent.capture());
        Email email = sent.getValue();
        assertThat(email.to()).isEqualTo(aliceEmail);
        assertThat(email.subject()).startsWith("Call booked: Jean Dupont, ");
        assertThat(email.text()).contains("Jean Dupont booked a call with you", "(Paris time), 30 min", "Company: Capgemini",
            "Email: jean@esn.example", "About the Java mission\nNext week", "/calendar?month=");
        assertThat(email.html()).contains("<td style=\"padding:4px 0;white-space:pre-line\">About the Java mission\nNext week</td>");
        assertThat(email.idempotencyKey()).startsWith("booking-notice/");

        // Cancelled before it was told: nothing; turned off: nothing either, and the setting reads back.
        clearInvocations(mailer);
        book(slug, starts.get(starts.size() - 2), null, null);
        long id = ((Number) ((List<Map<String, Object>>) call(alice, HttpMethod.GET, "/bookings", null, List.class).getBody()).stream()
            .filter(booking -> booking.get("cancelledAt") == null && booking.get("company") == null).findFirst().orElseThrow().get("id"))
            .longValue();
        call(alice, HttpMethod.POST, "/bookings/" + id + "/cancel", null, Map.class);
        Map<String, Object> settings = new HashMap<>(call(alice, HttpMethod.GET, "/users/notifications", null, Map.class).getBody());
        assertThat(settings).containsEntry("bookingEmails", true);
        settings.put("bookingEmails", false);
        assertThat(call(alice, HttpMethod.PUT, "/users/notifications", settings, Map.class).getBody()).containsEntry("bookingEmails", false);
        book(slug, starts.get(0), "Sopra", null);
        assertThat(notices.sendDue()).isZero();
        verify(mailer, never()).send(any());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bookings WHERE notice_sent_at IS NULL AND cancelled_at IS NULL",
            Integer.class)).isZero();
    }

    @SuppressWarnings("unchecked")
    private List<String> starts(String slug) {
        Map<String, Object> shown = exchange("/public/booking/" + slug, HttpMethod.GET, HttpEntity.EMPTY, Map.class).getBody();
        return ((List<Map<String, Object>>) shown.get("days")).stream().flatMap(day -> ((List<String>) day.get("starts")).stream()).toList();
    }

    private void book(String slug, String start, String company, String message) {
        Map<String, Object> request = new HashMap<>(Map.of("start", start, "name", "Jean Dupont", "email", "jean@esn.example"));
        request.put("company", company);
        request.put("message", message);
        assertThat(exchange("/public/booking/" + slug, HttpMethod.POST, new HttpEntity<>(request), Map.class).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
    }
}
