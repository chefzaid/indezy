package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;

/** French and English text of the weekly digest. */
enum WeeklyDigestLabel {
    SUBJECT("Votre semaine Indezy du %s", "Your Indezy week of %s"),
    HEADING("Bonjour %s, voici votre semaine", "Hello %s, here is your week"),
    PIPELINE("Pipeline", "Pipeline"),
    PIPELINE_LINE("%d opportunités en cours : %d premier contact, %d entretiens, %d offres",
        "%d open opportunities: %d first contact, %d interviewing, %d with an offer"),
    OVERDUE("Factures en retard de paiement", "Overdue invoices"),
    OVERDUE_LINE("%s, %s, %s, en retard de %d jours", "%s, %s, %s, %d days late"),
    INTERVIEWS("Entretiens des 7 prochains jours", "Interviews in the next 7 days"),
    FOLLOW_UPS("Relances à faire", "Follow-ups due"),
    FOLLOW_UP_LINE("%s, sans réponse depuis %d jours", "%s, no answer for %d days"),
    STALE("Opportunités sans nouvelles", "Opportunities gone quiet"),
    STALE_LINE("%s, rien depuis %d jours", "%s, nothing for %d days"),
    ENDING("Missions qui se terminent", "Missions ending"),
    ENDING_LINE("%s, fin le %s", "%s, ends on %s"),
    OFFERS_ONE("1 nouvelle offre correspond à votre profil", "1 new offer matches your profile"),
    OFFERS_MANY("%d nouvelles offres correspondent à votre profil", "%d new offers match your profile"),
    MORE("Et %d de plus", "And %d more"),
    OPEN("Ouvrir Indezy", "Open Indezy"),
    WHY("Vous recevez ce résumé car vous l'avez activé dans Profil → Notifications.",
        "You receive this digest because you turned it on in Profile → Notifications."),
    CHANGE("Gérer mes notifications", "Manage my notifications");

    private final String french;
    private final String english;

    WeeklyDigestLabel(String french, String english) {
        this.french = french;
        this.english = english;
    }

    String in(DocumentLanguage language) {
        return language == DocumentLanguage.EN ? english : french;
    }

    String format(DocumentLanguage language, Object... values) {
        return String.format(language.locale(), in(language), values);
    }
}
