package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.enums.OfferSite;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The keys of copies, on pairs seen on the boards on 2026-10-19. */
class OfferFingerprintTest {

    private static final String TEXT = "Dans le cadre du renforcement d'une direction de programmes à dimension internationale, " +
        "notre client recherche un program manager.";

    @Test
    void readsTitlesWithoutAccentsCaseOrGenderMarks() {
        assertThat(OfferFingerprint.title("Développeur .NET (H/F)")).isEqualTo("developpeur .net");
        assertThat(OfferFingerprint.title("Ingénieur DevOps F/H - Lyon")).isEqualTo("ingenieur devops lyon");
        assertThat(OfferFingerprint.title(null)).isEmpty();
    }

    @Test
    void tellsTheSamePublisherWhateverItsCaseOrTheSiteItPublishesOn() {
        String celad = OfferFingerprint.publisherKey(OfferSite.WORKDISPO, "Ingénieur FPGA secteur aérospatial (H/F)", "Celad");

        assertThat(OfferFingerprint.publisherKey(OfferSite.FREE_WORK, "Ingénieur FPGA – secteur aérospatial", "CELAD")).isEqualTo(celad);
        assertThat(OfferFingerprint.publisherKey(OfferSite.FREE_WORK, "Ingénieur FPGA", "CELAD")).isNotEqualTo(celad);
        // A site publishing its own missions is their publisher: Silkhom on Free-Work is Silkhom.
        assertThat(OfferFingerprint.publisherKey(OfferSite.SILKHOM, "Architecte infrastructure", null))
            .isEqualTo(OfferFingerprint.publisherKey(OfferSite.FREE_WORK, "Architecte infrastructure", "SILKHOM"))
            .hasSize(64);
        assertThat(OfferFingerprint.publisherKey(OfferSite.FREE_WORK, " ", "Celad")).isNull();
    }

    @Test
    void tellsTheSameTextUnderTheHeadingsBoardsAdd() {
        String original = OfferFingerprint.openingKey("Program Manager Cloud Transformation", TEXT);

        assertThat(OfferFingerprint.openingKey("Program manager cloud transformation (H/F)", "Description du poste\n\n" + TEXT))
            .isEqualTo(original);
        assertThat(OfferFingerprint.openingKey("Program Manager", TEXT)).isNotEqualTo(original);
        assertThat(OfferFingerprint.openingKey("Program Manager", "Contexte : mission courte.")).isNull();
        assertThat(OfferFingerprint.openingKey("Program Manager", null)).isNull();
        assertThat(OfferFingerprint.openingKey(null, TEXT)).isNull();
    }
}
