package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.FakeFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LeHibouReaderTest {

    private final LeHibouReader reader = new LeHibouReader();

    @Test
    void readsTheNewestMissionsAndLeavesOutProBono() throws Exception {
        FakeFetcher fetcher = new FakeFetcher().resource(LeHibouReader.SEARCH_URL, "lehibou-search.json");
        List<ScrapedOffer> offers = new ArrayList<>();

        reader.read(fetcher, page -> offers.addAll(page));

        assertThat(fetcher.postedBodies()).containsExactly("{}");
        assertThat(offers).hasSize(1);
        ScrapedOffer offer = offers.get(0);
        assertThat(offer.getUrl()).isEqualTo("https://www.lehibou.com/annonce/8ee73915-acbb-457c-9323-4b9bcaa24814");
        assertThat(offer.getTitle()).isEqualTo("Architecte fonctionnel");
        assertThat(offer.getCompany()).isNull();
        assertThat(offer.getRateMin()).isEqualTo(650);
        assertThat(offer.getRateMax()).isEqualTo(650);
        assertThat(offer.getCity()).isEqualTo("Meudon");
        assertThat(offer.getPostalCode()).isEqualTo("92190");
        assertThat(offer.getDepartmentName()).isEqualTo("Hauts-de-Seine");
        assertThat(offer.getCountryCode()).isEqualTo("fr");
        assertThat(offer.getLatitude()).isEqualTo(48.8);
        assertThat(offer.getLongitude()).isEqualTo(2.24);
        assertThat(offer.getWorkMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(offer.getRemoteDaysPerWeek()).isEqualTo(2);
        assertThat(offer.getDurationMonths()).isEqualTo(6);
        assertThat(offer.getStartDate()).isEqualTo(LocalDate.of(2027, 1, 13));
        assertThat(offer.getSkills()).containsExactly("UML");
        assertThat(offer.getPublishedAt()).isEqualTo(Instant.parse("2026-10-19T15:23:54Z"));
        assertThat(reader.site()).isEqualTo(OfferSite.LEHIBOU);
        assertThat(reader.domain()).isEqualTo("lehibou.com");
    }

    @Test
    void readsEachRemotePolicy() throws Exception {
        String missions = "{\"missions\":[" +
            "{\"id\":\"a\",\"title\":\"A\",\"remoteWork\":\"0d\"},{\"id\":\"b\",\"title\":\"B\",\"remoteWork\":\"5d\"}," +
            "{\"id\":\"c\",\"title\":\"C\",\"remoteWork\":\"partial\"},{\"id\":\"d\",\"title\":\"D\",\"remoteWork\":\"other\"}," +
            "{\"id\":\"e\",\"title\":\"E\",\"remoteWork\":\"full\"},{\"id\":\"f\",\"title\":\"F\"}]}";
        FakeFetcher fetcher = new FakeFetcher().page(LeHibouReader.SEARCH_URL, missions);
        List<ScrapedOffer> offers = new ArrayList<>();

        reader.read(fetcher, page -> offers.addAll(page));

        assertThat(offers).extracting(ScrapedOffer::getWorkMode).containsExactly(
            WorkMode.ONSITE, WorkMode.REMOTE, WorkMode.HYBRID, null, WorkMode.REMOTE, null);
    }
}
