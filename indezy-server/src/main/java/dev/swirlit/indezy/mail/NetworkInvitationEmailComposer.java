package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.springframework.stereotype.Component;

import java.util.Map;

import static dev.swirlit.indezy.mail.SavedSearchEmailComposer.escape;

/**
 * Writes the email inviting a contact to the member network (see docs/features.md#member-network), in the inviter's
 * language: who invites, what the network is, the link to join, and the link never to be invited again, also given as a
 * one-click unsubscribe.
 */
@Component
public class NetworkInvitationEmailComposer {

    private final MailProperties properties;

    public NetworkInvitationEmailComposer(MailProperties properties) {
        this.properties = properties;
    }

    public Email compose(String to, String contactFirstName, String inviterName, DocumentLanguage language, String token, String key) {
        boolean english = language == DocumentLanguage.EN;
        String hello = contactFirstName == null || contactFirstName.isBlank() ? (english ? "Hello," : "Bonjour,") :
            (english ? "Hello " : "Bonjour ") + contactFirstName.strip() + ",";
        String invite = english ? inviterName + " invites you to join them on the Indezy network: freelancers in IT who pass on missions," +
            " share their experience and their rates, and follow each other." :
            inviterName + " vous invite à le ou la rejoindre sur le réseau Indezy : des freelances de l'IT qui se transmettent des missions," +
            " partagent leurs retours d'expérience et leurs tarifs, et se suivent.";
        String free = english ? "Your account also runs your pipeline, your invoices and your timesheets." :
            "Votre compte gère aussi votre pipeline, vos factures et vos comptes rendus d'activité.";
        String page = properties.appUrl() + "/network-invitation#" + token;
        String action = english ? "See the invitation" : "Voir l'invitation";
        String never = english ? "Rather not be invited? From the same link, ask never to be invited to the network again." :
            "Vous préférez ne pas être invité ? Depuis le même lien, demandez à ne plus jamais être invité sur le réseau.";
        String subject = english ? inviterName + " invites you to the Indezy network" : inviterName + " vous invite sur le réseau Indezy";
        String html = "<p>" + escape(hello) + "</p><p>" + escape(invite) + "</p><p>" + escape(free) + "</p><p><a href=\"" +
            escape(page) + "\">" + escape(action) + "</a></p><p style=\"color:#6B7280;font-size:12px\">" + escape(never) + "</p>";
        String text = hello + "\n\n" + invite + "\n\n" + free + "\n\n" + action + " : " + page + "\n\n" + never + "\n";
        String unsubscribe = properties.appUrl() + "/api/public/network-invitations/" + token + "/decline";
        return new Email(to, subject, html, text, Map.of("List-Unsubscribe", "<" + unsubscribe + ">", "List-Unsubscribe-Post",
            "List-Unsubscribe=One-Click"), key);
    }
}
