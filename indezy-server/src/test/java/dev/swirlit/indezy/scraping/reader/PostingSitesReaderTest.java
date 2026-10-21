package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.FakeFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** FreelanceRepublik and EasyPartner, whose job pages carry a schema.org {@code JobPosting}, read from samples of them. */
class PostingSitesReaderTest {

    @Test
    void readsFreelanceRepublikMissionsWithTheirCommune() throws Exception {
        String mission = "https://www.freelancerepublik.com/missions/developpeur-scala-4e1a99f0";
        FakeFetcher fetcher = new FakeFetcher()
            .resource(FreelanceRepublikReader.SITEMAP, "freelancerepublik-sitemap.xml")
            .resource(mission, "freelancerepublik-mission.html");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        FreelanceRepublikReader reader = new FreelanceRepublikReader();

        reader.read(fetcher, batch);

        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId).containsExactly("developpeur-scala-4e1a99f0");
        ScrapedOffer offer = batch.offers.get(0);
        assertThat(offer.getUrl()).isEqualTo(mission);
        assertThat(offer.getTitle()).isEqualTo("Developpeur Scala");
        assertThat(offer.getCompany()).isEqualTo("Freelance Republik");
        assertThat(offer.getDescription()).isEqualTo("Ce que le poste implique\n\nNous recherchons un Développeur Scala pour renforcer une équipe TMA/MOE.");
        assertThat(offer.getCity()).isEqualTo("Paris");
        assertThat(offer.getCountryCode()).isEqualTo("FR");
        assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-08-03T08:50:37.990Z"));
        assertThat(offer.isFreelance()).isTrue();
        assertThat(reader.offerId(mission)).contains("developpeur-scala-4e1a99f0");
        assertThat(reader.offerId("https://www.freelancerepublik.com/missions")).isEmpty();
        assertThat(reader.site()).isEqualTo(OfferSite.FREELANCE_REPUBLIK);
        assertThat(reader.domain()).isEqualTo("freelancerepublik.com");
    }

    @Test
    void readsEasyPartnerFreelanceJobsFromTheirPageAndLeavesPermanentOnesOut() throws Exception {
        String lead = "https://easypartner.fr/job/lead-developpeur-php-telecom-paris-h-f/";
        String permanent = "https://easypartner.fr/job/developpeur-full-stack-typescript-saint-ouen-h-f/";
        FakeFetcher fetcher = new FakeFetcher()
            .resource(EasyPartnerReader.SITEMAP, "easypartner-sitemap.xml")
            .resource(lead, "easypartner-job.html")
            .resource(permanent, "easypartner-cdi.html");
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());
        EasyPartnerReader reader = new EasyPartnerReader();

        reader.read(fetcher, batch);

        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId)
            .containsExactly("lead-developpeur-php-telecom-paris-h-f", "developpeur-full-stack-typescript-saint-ouen-h-f");
        ScrapedOffer offer = batch.offers.get(0);
        assertThat(offer.getTitle()).isEqualTo("Lead Développeur PHP – Telecom – Paris (H/F)");
        assertThat(offer.getRateMin()).isEqualTo(490);
        assertThat(offer.getRateMax()).isEqualTo(550);
        assertThat(offer.getCity()).isEqualTo("Paris");
        assertThat(offer.getDescription()).isEqualTo("Contexte du poste\n\nRejoignez une entreprise innovante dans le secteur des " +
            "télécommunications.\n\nMissions\n\n– Piloter le développement des applications mobiles B2B.");
        assertThat(offer.isPublishedDayOnly()).isTrue();
        assertThat(offer.isFreelance()).isTrue();
        ScrapedOffer cdi = batch.offers.get(1);
        assertThat(cdi.isFreelance()).isFalse();
        assertThat(cdi.getRateMin()).isNull();
        assertThat(reader.offerId("https://easypartner.fr/job/")).isEmpty();
        assertThat(reader.site()).isEqualTo(OfferSite.EASY_PARTNER);
        assertThat(reader.domain()).isEqualTo("easypartner.fr");
    }
}
