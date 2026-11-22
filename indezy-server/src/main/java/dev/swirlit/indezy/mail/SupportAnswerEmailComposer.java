package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.springframework.stereotype.Component;

import java.util.Map;

import static dev.swirlit.indezy.mail.SavedSearchEmailComposer.escape;

/** Tells a member that support answered their request, with a link to read it in the app; the answer itself stays there. */
@Component
public class SupportAnswerEmailComposer {

    private final MailProperties properties;

    public SupportAnswerEmailComposer(MailProperties properties) {
        this.properties = properties;
    }

    public Email compose(String to, String subject, DocumentLanguage language, String key) {
        boolean english = language == DocumentLanguage.EN;
        String link = properties.appUrl() + "/support";
        String title = english ? "Indezy support answered your request" : "Le support d'Indezy a répondu à votre demande";
        String line = english ? "Your request \"" + subject + "\" has an answer. Read it in the app:" :
            "Votre demande « " + subject + " » a une réponse. Lisez-la dans l'application :";
        String action = english ? "Read the answer" : "Lire la réponse";
        String html = "<p>" + escape(line) + "</p><p><a href=\"" + escape(link) + "\">" + escape(action) + "</a></p>";
        return new Email(to, title, html, line + "\n" + link + "\n", Map.of(), key);
    }
}
