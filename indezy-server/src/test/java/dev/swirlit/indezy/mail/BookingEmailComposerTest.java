package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.Booking;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BookingEmailComposerTest {

    private final BookingEmailComposer composer = new BookingEmailComposer(
        new MailProperties("re_test", "Indezy <noreply@swirlit.dev>", "https://indezy.example/", "https://api.resend.com"));

    private static Booking booking(String company, String message) {
        Booking booking = new Booking();
        booking.setId(12L);
        booking.setStartsAt(Instant.parse("2026-11-03T08:00:00Z"));
        booking.setEndsAt(Instant.parse("2026-11-03T08:45:00Z"));
        booking.setName("Jean <Dupont>");
        booking.setEmail("jean@esn.example");
        booking.setCompany(company);
        booking.setMessage(message);
        return booking;
    }

    @Test
    void tellsTheCallInFrenchInParisTime() {
        Email email = composer.compose(booking(null, null), "alice@example.com", DocumentLanguage.FR);

        assertThat(email.to()).isEqualTo("alice@example.com");
        assertThat(email.subject()).isEqualTo("Appel réservé : Jean <Dupont>, mardi 3 novembre, 09h00");
        assertThat(email.text()).contains("Jean <Dupont> a réservé un appel avec vous", "Quand: mardi 3 novembre, 09h00 (heure de Paris), 45 min",
            "Email: jean@esn.example", "https://indezy.example/calendar?month=2026-11", "Gérer mes notifications: https://indezy.example/profile")
            .doesNotContain("Société", "Message");
        assertThat(email.html()).contains("Jean &lt;Dupont&gt;").doesNotContain("<Dupont>");
        assertThat(email.headers()).isEmpty();
        assertThat(email.idempotencyKey()).isEqualTo("booking-notice/12");
    }

    @Test
    void givesTheCompanyAndMessageInEnglish() {
        Email email = composer.compose(booking("ESN & Co", "Java <mission>"), "alice@example.com", DocumentLanguage.EN);

        assertThat(email.subject()).isEqualTo("Call booked: Jean <Dupont>, Tuesday 3 November, 09:00");
        assertThat(email.text()).contains("When: Tuesday 3 November, 09:00 (Paris time), 45 min", "Company: ESN & Co", "Message: Java <mission>",
            "Open the calendar: https://indezy.example/calendar?month=2026-11");
        assertThat(email.html()).contains("ESN &amp; Co", "Java &lt;mission&gt;");
    }
}
