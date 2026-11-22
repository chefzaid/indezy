package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static dev.swirlit.indezy.mail.SavedSearchEmailComposer.escape;

/**
 * Writes the email a member sends a contact through Indezy (see docs/features.md#emails-sent-from-indezy): their subject
 * and message as they typed them, from "their name via Indezy" with answers going to their own address, and a line
 * under it saying so. With open tracking that line also says the sender is told when it is opened, and the HTML carries
 * a one-pixel image whose address holds the email's secret; the plain-text part has no image, so nothing is counted
 * when it is read as text.
 */
@Component
public class ContactEmailComposer {

    private final MailProperties properties;

    public ContactEmailComposer(MailProperties properties) {
        this.properties = properties;
    }

    /** Who sends an email: their name, and the address answers go to. */
    public record Sender(String name, String replyTo) {
    }

    /** The email to {@code to}; {@code token} is null without open tracking. */
    public Email compose(String to, String subject, String body, Sender sender, DocumentLanguage language, String token, String key) {
        String notice = notice(sender.name(), language, token != null);
        StringBuilder html = new StringBuilder();
        for (String paragraph : body.strip().split("\\R\\s*\\R")) {
            html.append("<p>").append(escape(paragraph.strip()).replaceAll("\\R", "<br>")).append("</p>");
        }
        html.append("<p style=\"color:#6b7280;font-size:12px\">").append(escape(notice)).append("</p>");
        if (token != null) {
            html.append("<img src=\"").append(escape(pixel(token))).append("\" width=\"1\" height=\"1\" alt=\"\" style=\"display:block;border:0\">");
        }
        String text = body.strip() + "\n\n--\n" + notice + "\n";
        return new Email(to, subject.strip(), html.toString(), text, Map.of(), key, sender.name(), sender.replyTo(), List.of());
    }

    /** The copy the sender gets: the same message, without the tracking image, so reading it counts nothing. */
    public Email copy(String to, String recipientName, String subject, String body, DocumentLanguage language, String key) {
        boolean english = language == DocumentLanguage.EN;
        String intro = english ? "Copy of your email to " + recipientName + ", sent through Indezy." :
            "Copie de votre e-mail à " + recipientName + ", envoyé via Indezy.";
        StringBuilder html = new StringBuilder("<p style=\"color:#6b7280;font-size:12px\">").append(escape(intro)).append("</p>");
        for (String paragraph : body.strip().split("\\R\\s*\\R")) {
            html.append("<p>").append(escape(paragraph.strip()).replaceAll("\\R", "<br>")).append("</p>");
        }
        return new Email(to, (english ? "Copy: " : "Copie : ") + subject.strip(), html.toString(), intro + "\n\n" + body.strip() + "\n", Map.of(),
            key, null, null, List.of());
    }

    /** The tracking image's address. */
    public String pixel(String token) {
        return properties.appUrl() + "/api/public/emails/" + token + ".gif";
    }

    static String notice(String senderName, DocumentLanguage language, boolean tracked) {
        if (language == DocumentLanguage.EN) {
            return senderName + " sent you this email through Indezy" + (tracked ? ", which tells them when it is opened" : "") +
                ". Replying answers " + senderName + " directly.";
        }
        return senderName + " vous a envoyé cet e-mail via Indezy" + (tracked ? ", qui l'informe de son ouverture" : "") +
            ". Répondre à ce message répond directement à " + senderName + ".";
    }
}
