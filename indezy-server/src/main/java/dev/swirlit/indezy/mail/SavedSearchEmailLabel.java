package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;

/** French and English text of the saved search emails. */
enum SavedSearchEmailLabel {
    SUBJECT_ONE("1 nouvelle offre : %s", "1 new offer: %s"),
    SUBJECT_MANY("%d nouvelles offres : %s", "%d new offers: %s"),
    HEADING_ONE("1 nouvelle offre pour votre recherche « %s »", "1 new offer for your search “%s”"),
    HEADING_MANY("%d nouvelles offres pour votre recherche « %s »", "%d new offers for your search “%s”"),
    MORE("Et %d autres dans Indezy", "And %d more in Indezy"),
    SEE_ALL("Voir toutes les offres", "See every offer"),
    RATE_RANGE("%d–%d € / jour", "€%d–%d / day"),
    RATE_ONE("%d € / jour", "€%d / day"),
    REMOTE("Télétravail complet", "Full remote"),
    HYBRID("Hybride", "Hybrid"),
    HYBRID_DAYS("Hybride, %d j de télétravail / sem.", "Hybrid, %d remote days a week"),
    ONSITE("Sur site", "On site"),
    DURATION("%d mois", "%d months"),
    MATCH("Correspondance %d %%", "%d%% match"),
    SUSPICIOUS("À vérifier", "To check"),
    WHY("Vous recevez cet email car vous avez demandé à Indezy les nouvelles offres de la recherche « %s ».",
        "You receive this email because you asked Indezy for the new offers of the search “%s”."),
    CHANGE("Changer la fréquence", "Change how often"),
    UNSUBSCRIBE("Ne plus recevoir ces emails", "Stop these emails"),
    STOPPED_SUBJECT("Emails arrêtés : %s", "Emails stopped: %s"),
    STOPPED_HEADING("Nous n'envoyons plus les offres de « %s »", "We no longer send the offers of “%s”"),
    STOPPED_BODY("Aucune offre n'a été ouverte depuis ces emails en 60 jours, nous avons donc arrêté de les envoyer. " +
        "Vous pouvez les réactiver depuis la recherche dans Indezy.",
        "No offer was opened from these emails in 60 days, so we stopped sending them. You can turn them back on from the " +
            "search in Indezy."),
    STOPPED_ACTION("Ouvrir mes recherches", "Open my searches");

    private final String french;
    private final String english;

    SavedSearchEmailLabel(String french, String english) {
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
