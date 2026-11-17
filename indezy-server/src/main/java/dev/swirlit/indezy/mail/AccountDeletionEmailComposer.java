package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import static dev.swirlit.indezy.mail.SavedSearchEmailComposer.escape;

/**
 * Writes the email that confirms an account's deletion was asked for, in the account's language: the day it will be
 * deleted for good, and that signing in before then restores it (so a deletion the person did not ask for can be undone).
 */
@Component
public class AccountDeletionEmailComposer {

    private final MailProperties properties;

    public AccountDeletionEmailComposer(MailProperties properties) {
        this.properties = properties;
    }

    public Email compose(String firstName, String to, DocumentLanguage language, LocalDate purgeOn, String key) {
        boolean english = language == DocumentLanguage.EN;
        String day = purgeOn.format(DateTimeFormatter.ofPattern(english ? "MMMM d, yyyy" : "d MMMM yyyy", english ? Locale.ENGLISH : Locale.FRENCH));
        String hello = firstName == null || firstName.isBlank() ? (english ? "Hello," : "Bonjour,") :
            (english ? "Hello " : "Bonjour ") + firstName + ",";
        String held = english ? "Your Indezy account is on hold, as you asked: nobody can sign in to it, and it no longer shows in the network." :
            "Votre compte Indezy est suspendu, comme vous l'avez demandé : personne ne peut s'y connecter, et il n'apparaît plus dans le réseau.";
        String purge = english ? "On " + day + ", it will be deleted for good with everything it holds; this cannot be undone." :
            "Le " + day + ", il sera supprimé définitivement avec tout ce qu'il contient ; ce ne pourra pas être annulé.";
        String restore = english ? "Changed your mind, or did not ask for this? Sign in before then and choose Restore my account." :
            "Vous avez changé d'avis, ou ne l'avez pas demandé ? Connectez-vous d'ici là et choisissez Restaurer mon compte.";
        String link = properties.appUrl() + "/login";
        String subject = english ? "Your Indezy account will be deleted on " + day : "Votre compte Indezy sera supprimé le " + day;
        String html = "<p>" + escape(hello) + "</p><p>" + escape(held) + "</p><p>" + escape(purge) + "</p><p>" + escape(restore) +
            "</p><p><a href=\"" + escape(link) + "\">" + escape(link) + "</a></p>";
        String text = hello + "\n\n" + held + "\n\n" + purge + "\n\n" + restore + "\n" + link + "\n";
        return new Email(to, subject, html, text, Map.of(), key);
    }
}
