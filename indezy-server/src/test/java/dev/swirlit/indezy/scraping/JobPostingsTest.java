package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.enums.WorkMode;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JobPostingsTest {

    @Test
    void readsAContractorPosting() {
        ScrapedOffer offer = JobPostings.read(Jsoup.parse(FakeFetcher.read("workdispo-job.html"))).orElseThrow().build();

        assertThat(offer.getTitle()).isEqualTo("Administrateur Linux (H/F)");
        assertThat(offer.getDescription()).isEqualTo("Contexte de la mission\n\nAdministrer un parc de serveurs Linux.");
        assertThat(offer.getCompany()).isEqualTo("Exemple Conseil");
        assertThat(offer.getCity()).isEqualTo("Paris");
        assertThat(offer.getRegionName()).isEqualTo("Ile-de-France");
        assertThat(offer.getCountryCode()).isEqualTo("FR");
        assertThat(offer.getRateMin()).isEqualTo(570);
        assertThat(offer.getRateMax()).isEqualTo(570);
        assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-10-05T05:32:54Z"));
        assertThat(offer.isPublishedDayOnly()).isFalse();
        assertThat(offer.getExpiresAt()).isEqualTo(Instant.parse("2026-12-05T06:32:54Z"));
        assertThat(offer.isFreelance()).isTrue();
    }

    @Test
    void findsThePostingInAGraphOrArrayAndReadsPermanentJobsAsClosed() {
        ScrapedOffer offer = JobPostings.read(Jsoup.parse(FakeFetcher.read("workdispo-cdi.html"))).orElseThrow().build();

        assertThat(offer.isFreelance()).isFalse();
        assertThat(offer.getPostalCode()).isEqualTo("69002");
        assertThat(offer.getCountryCode()).isEqualTo("FR");
        assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-10-18T22:00:00Z"));
        assertThat(offer.isPublishedDayOnly()).isTrue();
    }

    @Test
    void readsRatesRangesRemoteWorkAndSkillLists() {
        String html = "<script type=\"application/ld+json\">{\"@graph\":[{\"@type\":\"WebPage\"},{\"@type\":[\"JobPosting\"]," +
            "\"title\":\"Dev\",\"employmentType\":\"contractor\",\"jobLocationType\":\"TELECOMMUTE\",\"skills\":\"Java, Kafka\"," +
            "\"baseSalary\":{\"unitText\":\"DAY\",\"value\":{\"minValue\":500,\"maxValue\":650}}}]}</script>" +
            "<script type=\"application/ld+json\">not json</script>";

        ScrapedOffer offer = JobPostings.read(Jsoup.parse(html)).orElseThrow().build();

        assertThat(offer.getRateMin()).isEqualTo(500);
        assertThat(offer.getRateMax()).isEqualTo(650);
        assertThat(offer.getWorkMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(offer.getSkills()).containsExactly("Java", "Kafka");
        assertThat(offer.isFreelance()).isTrue();
    }

    @Test
    void ignoresSalariesThatAreNotDailyAndPagesWithoutPosting() {
        String yearly = "<script type=\"application/ld+json\">{\"@type\":\"JobPosting\",\"title\":\"Dev\",\"employmentType\":\"FULL_TIME\"," +
            "\"baseSalary\":{\"value\":{\"unitText\":\"YEAR\",\"value\":55000}},\"jobLocation\":{\"address\":{\"addressCountry\":\"Belgique\"}}}</script>";

        ScrapedOffer offer = JobPostings.read(Jsoup.parse(yearly)).orElseThrow().build();

        assertThat(offer.getRateMin()).isNull();
        assertThat(offer.getCountryCode()).isEqualTo("BE");
        assertThat(JobPostings.read(Jsoup.parse("<script type=\"application/ld+json\">not json</script>"))).isEmpty();
        assertThat(JobPostings.read(Jsoup.parse("<html><body>Rien</body></html>"))).isEmpty();
    }

    @Test
    void readsADailyRateGivenAsANumberAndIgnoresAnImpossibleDay() {
        String html = "<script type=\"application/ld+json\">{\"@type\":\"JobPosting\",\"title\":\"Dev\",\"datePosted\":\"2026-13-45\"," +
            "\"baseSalary\":{\"unitText\":\"DAY\",\"value\":550}}</script>";

        ScrapedOffer offer = JobPostings.read(Jsoup.parse(html)).orElseThrow().build();

        assertThat(offer.getRateMin()).isEqualTo(550);
        assertThat(offer.getPublishedAt()).isNull();
        assertThat(JobPostings.read(Jsoup.parse("<script type=\"application/ld+json\">{\"@type\":\"JobPosting\"," +
            "\"title\":\"Consultant S&#xE9;curit&#xE9; &#x2013; N2\",\"hiringOrganization\":{\"name\":\"A &amp; B\"}}</script>"))
            .orElseThrow().build()).extracting(ScrapedOffer::getTitle, ScrapedOffer::getCompany).containsExactly("Consultant Sécurité – N2", "A & B");
    }
}
