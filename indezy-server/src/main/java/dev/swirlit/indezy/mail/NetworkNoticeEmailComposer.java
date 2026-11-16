package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.swirlit.indezy.mail.SavedSearchEmailComposer.escape;

/**
 * Writes the email of a member's network notifications, in the account's language: who did what, grouped as on the
 * notifications page, with the first words of the post concerned and a link to the notifications. It carries a one-click
 * unsubscribe (RFC 8058) and no tracking.
 */
@Component
public class NetworkNoticeEmailComposer {

    private static final String ACCENT = "#4F46E5";
    private static final String MUTED = "#6B7280";

    private final MailProperties properties;

    public NetworkNoticeEmailComposer(MailProperties properties) {
        this.properties = properties;
    }

    /** A group of notifications: its kind, the first member's name, how many others, and the post's first words. */
    public record Line(NetworkNotificationType type, String actor, int others, String excerpt) {

        @Override
        public String toString() {
            return "Line[" + type + "]";
        }
    }

    /** Who the email goes to, in which language, its unsubscribe token, the groups, and its idempotency key. */
    public record Notices(String firstName, String recipient, DocumentLanguage language, String token, List<Line> lines, String key) {

        public Notices {
            lines = List.copyOf(lines);
        }

        @Override
        public String toString() {
            return "Notices[" + key + "]";
        }
    }

    public Email compose(Notices notices) {
        boolean english = notices.language() == DocumentLanguage.EN;
        String subject = english ? "News from the member network" : "Du nouveau sur le réseau";
        String hello = notices.firstName() == null || notices.firstName().isBlank() ? (english ? "Hello," : "Bonjour,") :
            (english ? "Hello " : "Bonjour ") + notices.firstName() + ",";
        StringBuilder html = new StringBuilder("<p>").append(escape(hello)).append("</p><ul>");
        StringBuilder text = new StringBuilder(hello).append("\n\n");
        for (Line line : notices.lines()) {
            String sentence = sentence(line, english);
            html.append("<li>").append(escape(sentence)).append("</li>");
            text.append("- ").append(sentence).append('\n');
        }
        html.append("</ul>");
        String page = properties.appUrl() + "/network/notifications";
        String open = english ? "See my notifications" : "Voir mes notifications";
        html.append("<p style=\"margin:24px 0\"><a href=\"").append(escape(page)).append("\" style=\"color:").append(ACCENT).append("\">")
            .append(escape(open)).append("</a></p>");
        text.append('\n').append(open).append(": ").append(page).append("\n\n");
        String why = english ? "You receive this email for the kinds of notification you chose to be emailed." :
            "Vous recevez cet e-mail pour les types de notification que vous avez choisi de recevoir par e-mail.";
        String change = english ? "Manage my notifications" : "Gérer mes notifications";
        String settings = properties.appUrl() + "/profile";
        html.append("<p style=\"color:").append(MUTED).append(";font-size:12px;border-top:1px solid #E5E7EB;padding-top:16px\">")
            .append(escape(why)).append("<br><a href=\"").append(escape(settings)).append("\" style=\"color:").append(MUTED).append("\">")
            .append(escape(change)).append("</a></p>");
        text.append("--\n").append(why).append('\n').append(change).append(": ").append(settings).append('\n');
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("List-Unsubscribe", "<" + properties.appUrl() + "/api/public/network-notifications/unsubscribe/" + notices.token() + ">");
        headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        return new Email(notices.recipient(), subject, SavedSearchEmailComposer.page(html), text.toString(), headers, notices.key());
    }

    private static String english(NetworkNotificationType type) {
        return switch (type) {
            case FOLLOW -> "followed you";
            case FOLLOW_REQUEST -> "asked to follow you";
            case FOLLOW_ACCEPTED -> "accepted your request to follow";
            case LIKE -> "liked your post";
            case COMMENT_LIKE -> "liked your comment";
            case COMMENT -> "commented on your post";
            case REPLY -> "replied to your comment";
            case MENTION -> "mentioned you";
            case REPOST -> "reposted your post";
            default -> "did something";
        };
    }

    /** The French verb, plural when others did the same. */
    private static String french(NetworkNotificationType type, boolean many) {
        return switch (type) {
            case FOLLOW -> many ? "vous suivent désormais" : "vous suit désormais";
            case FOLLOW_REQUEST -> many ? "demandent à vous suivre" : "demande à vous suivre";
            case FOLLOW_ACCEPTED -> many ? "ont accepté votre demande de suivi" : "a accepté votre demande de suivi";
            case LIKE -> many ? "ont aimé votre publication" : "a aimé votre publication";
            case COMMENT_LIKE -> many ? "ont aimé votre commentaire" : "a aimé votre commentaire";
            case COMMENT -> many ? "ont commenté votre publication" : "a commenté votre publication";
            case REPLY -> many ? "ont répondu à votre commentaire" : "a répondu à votre commentaire";
            case MENTION -> many ? "vous ont mentionné" : "vous a mentionné";
            case REPOST -> many ? "ont republié votre publication" : "a republié votre publication";
            default -> many ? "ont agi" : "a agi";
        };
    }

    /** "Léo Martin and 2 others liked your post “Retour sur Kafka…”", in the language asked. */
    static String sentence(Line line, boolean english) {
        String others = english ? (line.others() == 1 ? " other" : " others") : (line.others() == 1 ? " autre" : " autres");
        String who = line.others() == 0 ? line.actor() : line.actor() + (english ? " and " : " et ") + line.others() + others;
        boolean many = line.others() > 0;
        String what = english ? english(line.type()) : french(line.type(), many);
        return who + " " + what + (line.excerpt() == null ? "" : " “" + line.excerpt() + "”");
    }
}
