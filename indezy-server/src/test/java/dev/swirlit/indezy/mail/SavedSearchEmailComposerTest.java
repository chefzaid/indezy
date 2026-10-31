package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.dto.OfferMatchDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.OfferSuspicion;
import dev.swirlit.indezy.model.enums.WorkMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SavedSearchEmailComposerTest {

    private static final Instant CUTOFF = Instant.parse("2026-10-23T08:00:00Z");
    private static final String TOKEN = "tok3n-Of-The_Search";

    private final SavedSearchEmailComposer composer = new SavedSearchEmailComposer(
        new MailProperties("re_test", "Indezy <noreply@swirlit.dev>", "https://indezy.example/", "https://api.resend.test"));

    @Test
    void writesTheNewOffersInFrenchWithTheirFactsAndLinksToTheApp() {
        Email email = composer.digest(new SavedSearchEmailComposer.Digest(4L, "Java à Lyon", "alice@example.com", DocumentLanguage.FR, TOKEN,
            List.of(offer(7L, "Développeur <b>Java</b> & Kafka", WorkMode.HYBRID, 2, new OfferMatchDto(83, List.of("Java"), null, null, null), null),
                offer(8L, "Architecte", WorkMode.REMOTE, 5, null, List.of(OfferSuspicion.RATE_OUTSIDE_MARKET))), 2, CUTOFF));

        assertThat(email.to()).isEqualTo("alice@example.com");
        assertThat(email.subject()).isEqualTo("2 nouvelles offres : Java à Lyon");
        assertThat(email.idempotencyKey()).isEqualTo("saved-search/4/" + CUTOFF.getEpochSecond());
        // Marked up text is shown, never interpreted.
        assertThat(email.html()).contains("Développeur &lt;b&gt;Java&lt;/b&gt; &amp; Kafka").doesNotContain("<b>Java</b>")
            .contains("href=\"https://indezy.example/offers/7?search=4&amp;from=email\"")
            .contains("Exemple Conseil · Lyon · Hybride, 2 j de télétravail / sem. · 550–650 € / jour · 6 mois · Correspondance 83 %")
            .contains("Télétravail complet").contains("À vérifier")
            .contains("Voir toutes les offres")
            .contains("href=\"https://indezy.example/emails/unsubscribe#" + TOKEN + "\"");
        assertThat(email.text()).startsWith("2 nouvelles offres pour votre recherche « Java à Lyon »\n\nDéveloppeur <b>Java</b> & Kafka\n")
            .contains("https://indezy.example/offers/8?search=4&from=email")
            .contains("Ne plus recevoir ces emails: https://indezy.example/emails/unsubscribe#" + TOKEN);
        assertThat(email.headers())
            .containsEntry("List-Unsubscribe", "<https://indezy.example/api/public/saved-search-emails/unsubscribe/" + TOKEN + ">")
            .containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        assertThat(email).hasToString("Email[saved-search/4/" + CUTOFF.getEpochSecond() + "]");
    }

    @Test
    void writesInEnglishAndCountsTheOffersLeftOut() {
        Email email = composer.digest(new SavedSearchEmailComposer.Digest(4L, "Java", "alice@example.com", DocumentLanguage.EN, TOKEN,
            List.of(offer(7L, "Java developer", WorkMode.ONSITE, null, null, null)), 23, CUTOFF));

        assertThat(email.subject()).isEqualTo("23 new offers: Java");
        assertThat(email.html()).contains("23 new offers for your search “Java”").contains("On site").contains("€550–650 / day")
            .contains("And 22 more in Indezy").contains("Stop these emails");
        assertThat(composer.digest(new SavedSearchEmailComposer.Digest(4L, "Java", "a@example.com", DocumentLanguage.EN, TOKEN,
            List.of(offer(7L, "Java developer", null, null, null, null)), 1, CUTOFF)).subject()).isEqualTo("1 new offer: Java");
    }

    @Test
    void tellsTheFactsAnOfferStates() {
        OfferDto bare = new OfferDto(9L, OfferSite.KICKLOX, "Kicklox", "https://app.kicklox.com/missions/9", "Pentester", null, null, null,
            null, 700, null, null, "69", "Rhône", null, "FR", null, WorkMode.HYBRID, null, null, null, null, null, List.of(), CUTOFF,
            OfferDatePrecision.EXACT, null, false, false, false, false, null, List.of(), null, null, null, null, null, null, null, null);

        assertThat(SavedSearchEmailComposer.facts(bare, DocumentLanguage.EN)).containsExactly("Rhône", "Hybrid", "€700 / day");
    }

    @Test
    void saysSendingStoppedWithoutAnUnsubscribeLink() {
        Email email = composer.stopped(4L, "Java", "alice@example.com", DocumentLanguage.FR, TOKEN, CUTOFF);

        assertThat(email.subject()).isEqualTo("Emails arrêtés : Java");
        assertThat(email.text()).contains("Aucune offre n'a été ouverte depuis ces emails en 60 jours").contains("https://indezy.example/offers");
        assertThat(email.headers()).isEmpty();
        assertThat(email.idempotencyKey()).isEqualTo("saved-search/4/stopped/" + CUTOFF.getEpochSecond());
    }

    private static OfferDto offer(Long id, String title, WorkMode mode, Integer remoteDays, OfferMatchDto match, List<OfferSuspicion> suspicion) {
        return new OfferDto(id, OfferSite.FREE_WORK, "Free-Work", "https://www.free-work.com/" + id, title, "Exemple Conseil", null, null,
            550, 650, "Lyon", "69002", "69", "Rhône", "Auvergne-Rhône-Alpes", "FR", null, mode, remoteDays, 6, null, null, null, List.of("Java"),
            CUTOFF, OfferDatePrecision.EXACT, null, false, false, false, false, null, List.of(), match, null, null, null, suspicion, null, null, null);
    }
}
