package dev.swirlit.indezy.scraping;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SitemapsTest {

    @Test
    void readsPagesWithTheirLastChangeAndKeepsOnlyTheWantedOnes() throws Exception {
        FakeFetcher fetcher = new FakeFetcher().resource("https://www.workdispo.com/sitemap.xml", "workdispo-sitemap.xml");

        List<Sitemaps.Entry> entries = Sitemaps.read(fetcher, "https://www.workdispo.com/sitemap.xml", url -> url.contains("/job/"));

        assertThat(entries).extracting(Sitemaps.Entry::url).hasSize(4).allMatch(url -> url.contains("/job/"));
        assertThat(entries.get(0).lastModified()).isEqualTo(Instant.parse("2026-10-18T07:00:00Z"));
        assertThat(entries.get(3).lastModified()).isEqualTo(Instant.parse("2026-10-10T00:00:00Z"));
    }

    @Test
    void followsAnIndexOneLevelDown() throws Exception {
        String index = "<?xml version=\"1.0\"?><sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">" +
            "<sitemap><loc> https://nextmondays.com/sitemap-jobs.xml </loc></sitemap></sitemapindex>";
        FakeFetcher fetcher = new FakeFetcher()
            .page("https://nextmondays.com/sitemap.xml", index)
            .resource("https://nextmondays.com/sitemap-jobs.xml", "nextmondays-sitemap.xml");

        List<Sitemaps.Entry> entries = Sitemaps.read(fetcher, "https://nextmondays.com/sitemap.xml", url -> true);

        assertThat(entries).extracting(Sitemaps.Entry::url)
            .containsExactly("https://nextmondays.com/jobs/technicien-support-applicatif/03u712452", "https://nextmondays.com/about");
        assertThat(entries.get(0).lastModified()).isNull();
    }

    @Test
    void readsEveryLastmodShape() {
        assertThat(Sitemaps.instant("2026-10-14T16:34:36.991Z")).isEqualTo(Instant.parse("2026-10-14T16:34:36.991Z"));
        assertThat(Sitemaps.instant("2026-10-15")).isEqualTo(Instant.parse("2026-10-15T00:00:00Z"));
        assertThat(Sitemaps.instant("yesterday")).isNull();
    }

    @Test
    void skipsPagesWithoutAddress() {
        Document sitemap = Jsoup.parse("<urlset><url><loc> </loc></url><url><lastmod>2026-10-01</lastmod></url>" +
            "<url><loc>https://www.example.com/a</loc></url></urlset>", "", Parser.xmlParser());

        assertThat(Sitemaps.entries(sitemap, url -> true)).containsExactly(new Sitemaps.Entry("https://www.example.com/a", null));
    }
}
