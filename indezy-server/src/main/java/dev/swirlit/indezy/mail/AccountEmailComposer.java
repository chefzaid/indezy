package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static dev.swirlit.indezy.mail.SavedSearchEmailComposer.escape;

/**
 * The emails of an account signed in with a password (see docs/features.md#sign-in): the link confirming its address
 * after registering, and the link setting a new password. Each says what it is for and that it can be ignored; no tracking.
 */
@Component
public class AccountEmailComposer {

    private static final String ACCENT = "#4F46E5";
    private static final String MUTED = "#6B7280";

    private final MailProperties properties;

    public AccountEmailComposer(MailProperties properties) {
        this.properties = properties;
    }

    /** What an email says: its subject, the sentence before the button, the button, and what to do if it was not asked for. */
    private record Texts(String subject, String lead, String action, String ignore) {
    }

    /** The email confirming the address of an account just registered, its link valid until used. */
    public Email verification(String to, String firstName, String token, DocumentLanguage language) {
        boolean english = language == DocumentLanguage.EN;
        Texts texts = english ?
            new Texts("Confirm your email for Indezy", "Confirm this address to open your Indezy account:", "Confirm my email",
                "If you did not create an account, ignore this email: nothing will be opened.") :
            new Texts("Confirmez votre e-mail pour Indezy", "Confirmez cette adresse pour ouvrir votre compte Indezy :", "Confirmer mon e-mail",
                "Si vous n'avez pas créé de compte, ignorez cet e-mail : rien ne sera ouvert.");
        return compose(to, firstName, english, texts, properties.appUrl() + "/verify-email?token=" + encode(token), "account-verify-" + key(token));
    }

    /** The email whose link sets a new password, valid an hour. */
    public Email reset(String to, String firstName, String token, DocumentLanguage language) {
        boolean english = language == DocumentLanguage.EN;
        Texts texts = english ?
            new Texts("Reset your Indezy password", "Choose a new password for your Indezy account; the link works for an hour:",
                "Choose a new password", "If you did not ask for it, ignore this email: your password stays as it is.") :
            new Texts("Réinitialisez votre mot de passe Indezy",
                "Choisissez un nouveau mot de passe pour votre compte Indezy ; le lien fonctionne pendant une heure :",
                "Choisir un nouveau mot de passe", "Si vous ne l'avez pas demandé, ignorez cet e-mail : votre mot de passe reste inchangé.");
        return compose(to, firstName, english, texts, properties.appUrl() + "/reset-password?token=" + encode(token), "account-reset-" + key(token));
    }

    private static String encode(String token) {
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    /** The idempotency key's part from the link's secret: its first characters, enough to tell two emails apart. */
    private static String key(String token) {
        return token.substring(0, Math.min(12, token.length()));
    }

    private static Email compose(String to, String firstName, boolean english, Texts texts, String link, String key) {
        String hello = firstName == null || firstName.isBlank() ? (english ? "Hello," : "Bonjour,") :
            (english ? "Hello " : "Bonjour ") + firstName.strip() + ",";
        String html = "<p>" + escape(hello) + "</p><p>" + escape(texts.lead()) + "</p><p><a href=\"" + escape(link) + "\" style=\"display:inline-block;" +
            "padding:10px 16px;border-radius:6px;background:" + ACCENT + ";color:#ffffff;text-decoration:none\">" + escape(texts.action()) + "</a></p>" +
            "<p style=\"color:" + MUTED + "\">" + escape(texts.ignore()) + "</p>";
        String text = hello + "\n\n" + texts.lead() + "\n" + link + "\n\n" + texts.ignore();
        return new Email(to, texts.subject(), html, text, Map.of(), key, null, null, List.of());
    }
}
