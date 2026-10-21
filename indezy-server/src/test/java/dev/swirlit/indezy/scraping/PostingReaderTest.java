package dev.swirlit.indezy.scraping;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostingReaderTest {

    private static final String LINK = "https://www.welcometothejungle.com/fr/companies/acme/jobs/developpeur-java";
    private static final String POSTING = "<html><head><script type=\"application/ld+json\">{\"@context\":\"https://schema.org\"," +
        "\"@type\":\"JobPosting\",\"title\":\"Développeur Java\",\"description\":\"<p>Refonte d'un SI bancaire.</p>\"," +
        "\"employmentType\":%s,\"hiringOrganization\":{\"name\":\"Acme\"},\"jobLocation\":{\"address\":{\"addressLocality\":\"Lyon\"," +
        "\"postalCode\":\"69002\",\"addressCountry\":\"FR\"}},\"baseSalary\":{\"value\":{\"minValue\":550,\"maxValue\":650," +
        "\"unitText\":\"DAY\"}}}</script></head><body><h1>Développeur Java</h1></body></html>";

    private final SiteHttpClient http = mock(SiteHttpClient.class);
    private final FakeFetcher fetcher = new FakeFetcher();
    private final PostingReader reader = new PostingReader(http, host -> {
        if (host.endsWith(".internal")) {
            throw new UnknownHostException(host);
        }
        return new InetAddress[] {InetAddress.getByName(host.startsWith("intranet") ? "10.1.2.3" : "52.95.110.1")};
    });

    @Test
    void readsTheJobPostingOfAPageOnAnotherSite() throws Exception {
        when(http.fetcherFor(eq("welcometothejungle.com"), any())).thenReturn(fetcher);
        fetcher.page(LINK, POSTING.formatted("[\"CONTRACTOR\",\"FULL_TIME\"]"));

        PostingReader.Reading reading = reader.read(URI.create(LINK));

        assertThat(reading.outcome()).isEqualTo(PostingReader.Outcome.FOUND);
        assertThat(reading.offer().getTitle()).isEqualTo("Développeur Java");
        assertThat(reading.offer().getCompany()).isEqualTo("Acme");
        assertThat(reading.offer().getCity()).isEqualTo("Lyon");
        assertThat(reading.offer().getRateMax()).isEqualTo(650);
        assertThat(reading.offer().getDescription()).isEqualTo("Refonte d'un SI bancaire.");
        assertThat(reading.offer().getUrl()).isEqualTo(LINK);
    }

    @Test
    void refusesAPostingThatStatesASalariedContract() throws Exception {
        when(http.fetcherFor(eq("welcometothejungle.com"), any())).thenReturn(fetcher);
        fetcher.page(LINK, POSTING.formatted("\"FULL_TIME\""));

        assertThat(reader.read(URI.create(LINK)).outcome()).isEqualTo(PostingReader.Outcome.NOT_FREELANCE);
    }

    @Test
    void readsThePageTitleAndSummaryWithoutAPosting() {
        ScrapedOffer offer = PostingReader.posting(Jsoup.parse("<html><head><title>Lead Data | Acme</title>" +
            "<meta property=\"og:title\" content=\"Lead Data H/F\"><meta name=\"description\" content=\"Mission de 6 mois à Nantes.\">" +
            "</head><body></body></html>"), LINK);

        assertThat(offer.getTitle()).isEqualTo("Lead Data H/F");
        assertThat(offer.getDescription()).isEqualTo("Mission de 6 mois à Nantes.");
        assertThat(offer.isFreelance()).isTrue();
        assertThat(PostingReader.posting(Jsoup.parse("<html><body><p>Rien</p></body></html>"), LINK)).isNull();
        assertThat(PostingReader.posting(Jsoup.parse("<html><body><h1>Architecte cloud</h1></body></html>"), LINK).getTitle())
            .isEqualTo("Architecte cloud");
    }

    @Test
    void neverReadsAHostOffThePublicInternet() throws Exception {
        assertThat(reader.read(URI.create("https://intranet.acme.com/jobs/1")).outcome()).isEqualTo(PostingReader.Outcome.UNAVAILABLE);
        assertThat(reader.read(URI.create("https://jobs.acme.internal/1")).outcome()).isEqualTo(PostingReader.Outcome.UNAVAILABLE);
        verify(http, never()).fetcherFor(any(), any());
    }

    @Test
    void tellsAPageGoneFromASiteThatCannotBeRead() throws Exception {
        when(http.fetcherFor(eq("welcometothejungle.com"), any())).thenReturn(fetcher);
        fetcher.status(LINK, 404);
        assertThat(reader.read(URI.create(LINK)).outcome()).isEqualTo(PostingReader.Outcome.NONE);
        fetcher.status(LINK, 503);
        assertThat(reader.read(URI.create(LINK)).outcome()).isEqualTo(PostingReader.Outcome.UNAVAILABLE);
        fetcher.page(LINK, "<html><body><p>Erreur</p></body></html>");
        assertThat(reader.read(URI.create(LINK)).outcome()).isEqualTo(PostingReader.Outcome.NONE);
    }
}
