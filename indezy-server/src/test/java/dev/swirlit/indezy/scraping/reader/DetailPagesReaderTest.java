package dev.swirlit.indezy.scraping.reader;

import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.FakeFetcher;
import dev.swirlit.indezy.scraping.PageFetcher;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import dev.swirlit.indezy.scraping.SiteResponseException;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DetailPagesReaderTest {

    private static final String SITE = "https://www.example.com/offre/";

    @Test
    void readsEachUnknownOfferOnceAndHandsOnPagesGoneAsClosed() throws Exception {
        FakeFetcher fetcher = new FakeFetcher() {
            @Override
            public String get(String url) throws IOException {
                if (url.endsWith("retiree")) {
                    throw new SiteResponseException(410, "GET answered 410");
                }
                return super.get(url);
            }
        }.page(SITE + "nouvelle", "<h1>Data engineer</h1>").page(SITE + "vide", "<p>Plus rien</p>");
        ListedSite site = new ListedSite(List.of(
            new DetailPagesReader.Entry(SITE + "sans-id", null, null),
            new DetailPagesReader.Entry(SITE + "vide", "vide", null),
            new DetailPagesReader.Entry(SITE + "nouvelle", "nouvelle", Instant.parse("2026-10-18T00:00:00Z")),
            new DetailPagesReader.Entry(SITE + "nouvelle", "nouvelle", Instant.parse("2026-10-18T00:00:00Z")),
            new DetailPagesReader.Entry(SITE + "retiree", "retiree", Instant.parse("2026-10-01T00:00:00Z"))));
        WorkDispoReaderTest.Batch batch = new WorkDispoReaderTest.Batch(Set.of());

        site.read(fetcher, batch);

        // Read once each, the entry without identifier never; the 410 is answered before the fake records the request.
        assertThat(fetcher.requests()).containsExactly(SITE + "nouvelle", SITE + "vide");
        assertThat(batch.offers).extracting(ScrapedOffer::getExternalId).containsExactly("nouvelle", "retiree", "vide");
        assertThat(batch.offers).extracting(ScrapedOffer::isFreelance).containsExactly(true, false, false);
        assertThat(batch.offers.get(0).getTitle()).isEqualTo("Data engineer");
        assertThat(batch.offers.get(0).getUrl()).isEqualTo(SITE + "nouvelle");
    }

    /** A site listing the given entries, each page an offer titled after its heading. */
    private static final class ListedSite extends DetailPagesReader {
        private final List<Entry> entries;

        ListedSite(List<Entry> entries) {
            this.entries = entries;
        }

        @Override
        public OfferSite site() {
            return OfferSite.WORKDISPO;
        }

        @Override
        public String domain() {
            return "example.com";
        }

        @Override
        List<Entry> entries(PageFetcher fetcher) {
            return entries;
        }

        @Override
        ScrapedOffer offer(Entry entry, Document page) {
            return page.selectFirst("h1") == null ? null : ScrapedOffer.builder().title(page.selectFirst("h1").text()).build();
        }
    }
}
