package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import static dev.swirlit.indezy.mail.SavedSearchEmailComposer.escape;

/**
 * Writes the email that carries a testimonial's link to the person asked (see docs/features.md#testimonials), in the
 * freelancer's language: who asks, what for, the link to write it without an account, until when it opens, and that it
 * can be ignored. The address is the person's own, and the link is theirs alone.
 */
@Component
public class TestimonialRequestEmailComposer {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final MailProperties properties;

    public TestimonialRequestEmailComposer(MailProperties properties) {
        this.properties = properties;
    }

    public Email compose(String recipientName, String to, String freelancerName, DocumentLanguage language, String token, Instant expiresAt,
                         String key) {
        boolean english = language == DocumentLanguage.EN;
        String until = expiresAt.atZone(PARIS).toLocalDate()
            .format(DateTimeFormatter.ofPattern(english ? "MMMM d, yyyy" : "d MMMM yyyy", english ? Locale.ENGLISH : Locale.FRENCH));
        String hello = (english ? "Hello " : "Bonjour ") + recipientName + ",";
        String ask = english ? freelancerName + " asks you for a short recommendation about working together, which they may show on their" +
            " public page with your name once they have read it." :
            freelancerName + " vous demande une courte recommandation sur votre collaboration, qu'il ou elle pourra afficher avec votre nom" +
            " sur sa page publique après l'avoir relue.";
        String how = english ? "Write it from this link, without creating an account. It is yours alone, opens until " + until +
            " and is answered once." :
            "Écrivez-la depuis ce lien, sans créer de compte. Il vous est personnel, s'ouvre jusqu'au " + until + " et ne sert qu'une fois.";
        String ignore = english ? "Not for you? Simply ignore this email." : "Ce message ne vous concerne pas ? Ignorez-le simplement.";
        String link = properties.appUrl() + "/testimonial#" + token;
        String subject = english ? freelancerName + " asks you for a recommendation" : freelancerName + " vous demande une recommandation";
        String action = english ? "Write the recommendation" : "Écrire la recommandation";
        String html = "<p>" + escape(hello) + "</p><p>" + escape(ask) + "</p><p><a href=\"" + escape(link) + "\">" + escape(action) +
            "</a></p><p>" + escape(how) + "</p><p>" + escape(ignore) + "</p>";
        String text = hello + "\n\n" + ask + "\n\n" + action + " : " + link + "\n\n" + how + "\n\n" + ignore + "\n";
        return new Email(to, subject, html, text, Map.of(), key);
    }
}
