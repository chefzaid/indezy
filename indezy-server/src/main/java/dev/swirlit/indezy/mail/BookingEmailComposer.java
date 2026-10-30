package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.Booking;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.swirlit.indezy.mail.SavedSearchEmailComposer.escape;

/**
 * Writes the email telling the freelancer a recruiter booked a call through the booking link, in the account's language:
 * when (Paris time, like the link's hours), who, and their message, with a link to the calendar. No tracking.
 */
@Component
public class BookingEmailComposer {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final String ACCENT = "#4F46E5";
    private static final String MUTED = "#6B7280";

    private final MailProperties properties;

    public BookingEmailComposer(MailProperties properties) {
        this.properties = properties;
    }

    public Email compose(Booking booking, String recipient, DocumentLanguage language) {
        boolean english = language == DocumentLanguage.EN;
        Locale locale = language.locale();
        ZonedDateTime start = booking.getStartsAt().atZone(PARIS);
        String when = start.format(DateTimeFormatter.ofPattern(english ? "EEEE d MMMM, HH:mm" : "EEEE d MMMM, HH'h'mm", locale));
        long minutes = Duration.between(booking.getStartsAt(), booking.getEndsAt()).toMinutes();
        String subject = english ? "Call booked: " + booking.getName() + ", " + when : "Appel réservé : " + booking.getName() + ", " + when;
        String heading = english ? booking.getName() + " booked a call with you" : booking.getName() + " a réservé un appel avec vous";

        List<String[]> facts = new ArrayList<>();
        facts.add(new String[] {english ? "When" : "Quand", when + (english ? " (Paris time), " : " (heure de Paris), ") + minutes + " min"});
        if (booking.getCompany() != null) {
            facts.add(new String[] {english ? "Company" : "Société", booking.getCompany()});
        }
        facts.add(new String[] {"Email", booking.getEmail()});
        if (booking.getMessage() != null) {
            facts.add(new String[] {"Message", booking.getMessage()});
        }

        StringBuilder html = new StringBuilder("<h1 style=\"font-size:20px;margin:0 0 16px\">").append(escape(heading)).append("</h1>")
            .append("<table style=\"border-collapse:collapse;margin:0 0 16px\">");
        StringBuilder text = new StringBuilder(heading).append("\n\n");
        for (String[] fact : facts) {
            html.append("<tr><td style=\"color:").append(MUTED).append(";padding:4px 16px 4px 0;vertical-align:top\">").append(escape(fact[0]))
                .append("</td><td style=\"padding:4px 0;white-space:pre-line\">").append(escape(fact[1])).append("</td></tr>");
            text.append(fact[0]).append(": ").append(fact[1]).append('\n');
        }
        html.append("</table>");
        String calendar = properties.appUrl() + "/calendar?month=" + start.getYear() + "-" + String.format("%02d", start.getMonthValue());
        String open = english ? "Open the calendar" : "Ouvrir le calendrier";
        html.append("<p style=\"margin:24px 0\"><a href=\"").append(escape(calendar)).append("\" style=\"color:").append(ACCENT).append("\">")
            .append(escape(open)).append("</a></p>");
        text.append('\n').append(open).append(": ").append(calendar).append("\n\n");
        String why = english ? "You receive this email because a call was booked through your booking link." :
            "Vous recevez cet e-mail car un appel a été réservé par votre lien de réservation.";
        String change = english ? "Manage my notifications" : "Gérer mes notifications";
        String settings = properties.appUrl() + "/profile";
        html.append("<p style=\"color:").append(MUTED).append(";font-size:12px;border-top:1px solid #E5E7EB;padding-top:16px\">")
            .append(escape(why)).append("<br><a href=\"").append(escape(settings)).append("\" style=\"color:").append(MUTED).append("\">")
            .append(escape(change)).append("</a></p>");
        text.append("--\n").append(why).append('\n').append(change).append(": ").append(settings).append('\n');
        return new Email(recipient, subject, SavedSearchEmailComposer.page(html), text.toString(), Map.of(),
            "booking-notice/" + booking.getId());
    }
}
