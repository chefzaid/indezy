package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.FakeFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreeWorkReaderTest {

    private final FreeWorkReader reader = new FreeWorkReader();

    @Test
    void readsContractorOffersAndDropsFakeListingsAndPermanentJobs() throws Exception {
        FakeFetcher fetcher = new FakeFetcher()
            .resource(FreeWorkReader.API + 1, "free-work-page.json")
            .page(FreeWorkReader.API + 2, "[]");
        List<ScrapedOffer> offers = new ArrayList<>();

        reader.read(fetcher, page -> offers.addAll(page));

        assertThat(fetcher.requests()).containsExactly(FreeWorkReader.API + 1, FreeWorkReader.API + 2);
        assertThat(offers).extracting(ScrapedOffer::getExternalId).containsExactly("900001", "900003", "900004");
        ScrapedOffer java = offers.get(0);
        assertThat(java.getUrl()).isEqualTo("https://www.free-work.com/fr/tech-it/job-mission/developpeur-java/developpeur-java-spring-h-f");
        assertThat(java.getTitle()).isEqualTo("Développeur Java Spring (H/F)");
        assertThat(java.getCompany()).isEqualTo("Exemple Conseil");
        assertThat(java.getDescription())
            .isEqualTo("Contexte\n\n• Refonte d'un SI bancaire\n• Java 21 & Spring Boot\n\n5 ans d'expérience minimum");
        assertThat(java.getRateMin()).isEqualTo(550);
        assertThat(java.getRateMax()).isEqualTo(650);
        assertThat(java.getCity()).isEqualTo("Paris");
        assertThat(java.getPostalCode()).isEqualTo("75008");
        assertThat(java.getRegionName()).isEqualTo("Île-de-France");
        assertThat(java.getLatitude()).isEqualTo(48.8534951);
        assertThat(java.getWorkMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(java.getDurationMonths()).isEqualTo(6);
        assertThat(java.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(java.getSeniority()).isEqualTo(Seniority.SENIOR);
        assertThat(java.getSkills()).containsExactly("Java", "Spring Boot", "Kafka");
        assertThat(java.getPublishedAt()).isEqualTo(Instant.parse("2026-10-19T08:47:42Z"));
        assertThat(java.getExpiresAt()).isEqualTo(Instant.parse("2026-12-18T22:59:59Z"));
        assertThat(java.isFreelance()).isTrue();
        assertThat(offers.get(1).isFreelance()).isFalse();
        ScrapedOffer cloud = offers.get(2);
        assertThat(cloud.getUrl()).endsWith("/job-mission/autre/architecte-cloud");
        assertThat(cloud.getWorkMode()).isEqualTo(WorkMode.ONSITE);
        assertThat(cloud.getDurationMonths()).isEqualTo(12);
        assertThat(cloud.getSeniority()).isEqualTo(Seniority.EXPERT);
    }

    @Test
    void stopsWhenTheBatchHasEnough() throws Exception {
        FakeFetcher fetcher = new FakeFetcher().resource(FreeWorkReader.API + 1, "free-work-page.json");

        reader.read(fetcher, page -> false);

        assertThat(fetcher.requests()).containsExactly(FreeWorkReader.API + 1);
    }

    @Test
    void reportsAnUnreadableAnswer() {
        FakeFetcher fetcher = new FakeFetcher().page(FreeWorkReader.API + 1, "<html>maintenance</html>");

        assertThatThrownBy(() -> reader.read(fetcher, page -> true)).isInstanceOf(IOException.class);
    }

    @Test
    void namesItsSiteAndDomain() {
        assertThat(reader.site()).isEqualTo(OfferSite.FREE_WORK);
        assertThat(reader.domain()).isEqualTo("free-work.com");
        assertThat(reader.newestFirst()).isTrue();
    }
}
