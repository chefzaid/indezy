package dev.swirlit.indezy.scraping;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LinkPreviewReaderTest {

    private static final String LINK = "https://www.blog.example.org/kafka-en-prod";

    private final SiteHttpClient http = mock(SiteHttpClient.class);
    private final FakeFetcher fetcher = new FakeFetcher();
    private final LinkPreviewReader reader = new LinkPreviewReader(http, host ->
        new InetAddress[] {InetAddress.getByName(host.startsWith("intranet") ? "10.1.2.3" : "52.95.110.1")});

    @Test
    void readsTheSiteTitleAndDescriptionFromTheOpenGraphTags() throws Exception {
        when(http.fetcherFor(eq("blog.example.org"), any())).thenReturn(fetcher);
        fetcher.page(LINK, "<html><head><title>Ignored</title><meta property=\"og:title\" content=\"  Kafka en   prod \">" +
            "<meta property=\"og:site_name\" content=\"Le blog\"><meta property=\"og:description\" content=\"" + "x".repeat(400) + "\">" +
            "</head></html>");
        LinkPreviewReader.Preview preview = reader.read(LINK).orElseThrow();
        assertThat(preview.title()).isEqualTo("Kafka en prod");
        assertThat(preview.site()).isEqualTo("Le blog");
        assertThat(preview.description()).hasSize(LinkPreviewReader.DESCRIPTION_MAX).endsWith("…");
        assertThat(preview.url()).isEqualTo(LINK);
    }

    @Test
    void fallsBackOnThePageTitleAndHostAndNeedsATitle() throws Exception {
        when(http.fetcherFor(eq("blog.example.org"), any())).thenReturn(fetcher);
        fetcher.page(LINK, "<html><head><title>Retour</title><meta name=\"description\" content=\"Un retour.\"></head></html>");
        assertThat(reader.read(LINK)).hasValueSatisfying(preview -> {
            assertThat(preview.site()).isEqualTo("blog.example.org");
            assertThat(preview.description()).isEqualTo("Un retour.");
        });
        fetcher.page(LINK, "<html><body>Nothing</body></html>");
        assertThat(reader.read(LINK)).isEmpty();
    }

    @Test
    void readsNothingOffThePublicInternetOrWhenThePageCannotBeRead() throws Exception {
        assertThat(reader.read("https://intranet.example.org/page")).isEmpty();
        assertThat(reader.read("https://blog.example.org:8443/page")).isEmpty();
        assertThat(reader.read("ftp://blog.example.org/page")).isEmpty();
        assertThat(reader.read("https://10.0.0.1/page")).isEmpty();
        assertThat(reader.read("not a link")).isEmpty();
        verify(http, never()).fetcherFor(any(), any());
        when(http.fetcherFor(eq("blog.example.org"), any())).thenReturn(fetcher.status(LINK, 503));
        assertThat(reader.read(LINK)).isEmpty();
    }
}
