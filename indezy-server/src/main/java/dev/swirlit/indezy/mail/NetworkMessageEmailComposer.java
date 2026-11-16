package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.swirlit.indezy.mail.SavedSearchEmailComposer.escape;

/**
 * Writes the email telling a member messages wait for them, in the account's language: who wrote and how many messages,
 * never their text, with a link to the messages. It carries a one-click unsubscribe (RFC 8058) and no tracking.
 */
@Component
public class NetworkMessageEmailComposer {

    private static final String ACCENT = "#4F46E5";
    private static final String MUTED = "#6B7280";

    private final MailProperties properties;

    public NetworkMessageEmailComposer(MailProperties properties) {
        this.properties = properties;
    }

    /** A conversation with unread messages: the other member's name and how many messages wait. */
    public record Waiting(String name, long count) {
    }

    /** Who the email goes to, in which language, its unsubscribe token, and the conversations waiting. */
    public record Unread(Long userId, String firstName, String recipient, DocumentLanguage language, String token, List<Waiting> waiting,
                         String key) {

        public Unread {
            waiting = List.copyOf(waiting);
        }

        @Override
        public String toString() {
            return "Unread[" + key + "]";
        }
    }

    public Email compose(Unread unread) {
        boolean english = unread.language() == DocumentLanguage.EN;
        long total = unread.waiting().stream().mapToLong(Waiting::count).sum();
        String subject = english ? (total == 1 ? "1 unread message" : total + " unread messages") + " on Indezy" :
            (total == 1 ? "1 message non lu" : total + " messages non lus") + " sur Indezy";
        String hello = unread.firstName() == null || unread.firstName().isBlank() ? (english ? "Hello," : "Bonjour,") :
            (english ? "Hello " : "Bonjour ") + unread.firstName() + ",";
        String lead = english ? "Members wrote to you on the member network:" : "Des membres vous ont écrit sur le réseau :";
        StringBuilder html = new StringBuilder("<p>").append(escape(hello)).append("</p><p>").append(escape(lead)).append("</p><ul>");
        StringBuilder text = new StringBuilder(hello).append("\n\n").append(lead).append('\n');
        for (Waiting waiting : unread.waiting()) {
            String count = waiting.count() == 1 ? "1 message" : waiting.count() + " messages";
            html.append("<li><strong>").append(escape(waiting.name())).append("</strong> · ").append(escape(count)).append("</li>");
            text.append("- ").append(waiting.name()).append(" · ").append(count).append('\n');
        }
        html.append("</ul>");
        String messages = properties.appUrl() + "/network/messages";
        String open = english ? "Read my messages" : "Lire mes messages";
        html.append("<p style=\"margin:24px 0\"><a href=\"").append(escape(messages)).append("\" style=\"color:").append(ACCENT).append("\">")
            .append(escape(open)).append("</a></p>");
        text.append('\n').append(open).append(": ").append(messages).append("\n\n");
        String why = english ? "You receive this email because messages stayed unread for an hour." :
            "Vous recevez cet e-mail car des messages sont restés non lus pendant une heure.";
        String change = english ? "Manage my notifications" : "Gérer mes notifications";
        String settings = properties.appUrl() + "/profile";
        html.append("<p style=\"color:").append(MUTED).append(";font-size:12px;border-top:1px solid #E5E7EB;padding-top:16px\">")
            .append(escape(why)).append("<br><a href=\"").append(escape(settings)).append("\" style=\"color:").append(MUTED).append("\">")
            .append(escape(change)).append("</a></p>");
        text.append("--\n").append(why).append('\n').append(change).append(": ").append(settings).append('\n');
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("List-Unsubscribe", "<" + properties.appUrl() + "/api/public/network-messages/unsubscribe/" + unread.token() + ">");
        headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        return new Email(unread.recipient(), subject, SavedSearchEmailComposer.page(html), text.toString(), headers, unread.key());
    }
}
