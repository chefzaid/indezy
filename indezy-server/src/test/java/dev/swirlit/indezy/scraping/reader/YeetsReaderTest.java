package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.scraping.FakeFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YeetsReaderTest {

    private final YeetsReader reader = new YeetsReader();

    @Test
    void readsOpenMissionsWithTheirSitemapAddress() throws Exception {
        FakeFetcher fetcher = new FakeFetcher()
            .resource(YeetsReader.SITEMAP, "yeets-sitemap.xml")
            .resource(YeetsReader.API + 0, "yeets-page.json");
        List<ScrapedOffer> offers = new ArrayList<>();

        reader.read(fetcher, page -> offers.addAll(page));

        assertThat(fetcher.requests()).containsExactly(YeetsReader.SITEMAP, YeetsReader.API + 0);
        assertThat(offers).extracting(ScrapedOffer::getExternalId)
            .containsExactly("1791969039778x693775314101469200", "1790843395137x539989663305695200");
        ScrapedOffer copilot = offers.get(0);
        assertThat(copilot.getUrl())
            .isEqualTo("https://www.yeets.fr/page-mission/consultant-microsoft-365-copilot-1791969039778x693775314101469200");
        assertThat(copilot.getRateMin()).isEqualTo(765);
        assertThat(copilot.getCity()).isEqualTo("Paris");
        assertThat(copilot.getCountryCode()).isEqualTo("FR");
        assertThat(copilot.getLatitude()).isEqualTo(48.8575475);
        assertThat(copilot.getWorkMode()).isEqualTo(WorkMode.HYBRID);
        assertThat(copilot.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(copilot.getSeniority()).isEqualTo(Seniority.CONFIRMED);
        assertThat(copilot.getDurationMonths()).isEqualTo(12);
        assertThat(copilot.getSkills()).containsExactly("copilot", "microsoft 365");
        assertThat(copilot.getPublishedAt()).isEqualTo(Instant.parse("2026-10-14T09:10:40.767Z"));
        ScrapedOffer php = offers.get(1);
        assertThat(php.getUrl()).isEqualTo("https://www.yeets.fr/page-mission/developpeur-php-full-stack-1790843395137x539989663305695200");
        assertThat(php.getWorkMode()).isEqualTo(WorkMode.ONSITE);
    }

    @Test
    void rebuildsAddressesWhenTheSitemapIsMissingAndPagesThroughCursors() throws Exception {
        String first = "{\"response\":{\"remaining\":1,\"results\":[{\"_id\":\"1x1\",\"missionTitre\":\"Tech Lead\"}]}}";
        String second = "{\"response\":{\"remaining\":0,\"results\":[{\"_id\":\"2x2\",\"missionTitre\":null}]}}";
        FakeFetcher fetcher = new FakeFetcher().page(YeetsReader.API + 0, first).page(YeetsReader.API + 100, second);
        List<ScrapedOffer> offers = new ArrayList<>();

        reader.read(fetcher, page -> offers.addAll(page));

        assertThat(offers).extracting(ScrapedOffer::getUrl).containsExactly(
            "https://www.yeets.fr/page-mission/tech-lead-1x1", "https://www.yeets.fr/page-mission/mission-2x2");
        assertThat(reader.site()).isEqualTo(OfferSite.YEETS);
        assertThat(reader.domain()).isEqualTo("yeets.fr");
    }
}
