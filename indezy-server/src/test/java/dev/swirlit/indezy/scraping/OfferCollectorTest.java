package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferLinkStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferCollectorTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final SiteHttpClient http = mock(SiteHttpClient.class);
    private final OfferNormalizer normalizer = mock(OfferNormalizer.class);
    private final OfferStore store = mock(OfferStore.class);
    private final OfferSiteRuns runs = mock(OfferSiteRuns.class);
    private final OfferProperties properties = new OfferProperties(true, Duration.ofMinutes(5), Duration.ofSeconds(2), 3,
        Duration.ofHours(1), 90, "IndezyBot/test", false, "http://127.0.0.1");

    @Test
    void stopsANewestFirstSiteAtTheFirstPageOfKnownOffers() {
        when(normalizer.normalize(eq(OfferSite.FREE_WORK), any(), any())).thenAnswer(invocation -> Optional.of(new JobOffer()));
        when(store.save(eq(OfferSite.FREE_WORK), anyList())).thenReturn(2, 0);
        PagedReader reader = new PagedReader(OfferSite.FREE_WORK, true, 10);

        OfferCollector.RunResult result = collector(reader).collect(OfferSite.FREE_WORK);

        assertThat(reader.pagesRead).isEqualTo(2);
        assertThat(result).isEqualTo(new OfferCollector.RunResult(2, 4, 2));
        verify(runs).succeeded(OfferSite.FREE_WORK, NOW, 4, 2);
    }

    @Test
    void readsAnUnsortedSiteUpToThePageBudget() {
        when(normalizer.normalize(eq(OfferSite.CHERRY_PICK), any(), any())).thenAnswer(invocation -> Optional.of(new JobOffer()));
        when(store.save(eq(OfferSite.CHERRY_PICK), anyList())).thenReturn(0);
        PagedReader reader = new PagedReader(OfferSite.CHERRY_PICK, false, 10);

        collector(reader).collect(OfferSite.CHERRY_PICK);

        assertThat(reader.pagesRead).isEqualTo(properties.maxPages());
    }

    @Test
    void goesOnPastAPageWhoseOffersWereAllDropped() {
        when(normalizer.normalize(eq(OfferSite.FREE_WORK), any(), any())).thenReturn(Optional.empty());
        PagedReader reader = new PagedReader(OfferSite.FREE_WORK, true, 2);

        collector(reader).collect(OfferSite.FREE_WORK);

        assertThat(reader.pagesRead).isEqualTo(2);
        verify(runs).succeeded(OfferSite.FREE_WORK, NOW, 0, 0);
    }

    @Test
    void setsAsideTheOffersLeftOutAndAnswersWhichAreKnown() {
        when(normalizer.normalize(eq(OfferSite.MINDQUEST), any(), any()))
            .thenAnswer(invocation -> ((ScrapedOffer) invocation.getArgument(1)).getExternalId().endsWith("a") ?
                Optional.of(new JobOffer()) : Optional.empty());
        when(store.known(OfferSite.MINDQUEST, List.of("x"))).thenReturn(java.util.Set.of("x"));
        java.util.List<java.util.Set<String>> answers = new ArrayList<>();
        PagedReader reader = new PagedReader(OfferSite.MINDQUEST, false, 1) {
            @Override
            public void read(PageFetcher fetcher, OfferBatch batch) throws IOException, InterruptedException {
                answers.add(batch.known(List.of("x")));
                super.read(fetcher, batch);
            }
        };

        collector(reader).collect(OfferSite.MINDQUEST);

        assertThat(answers).containsExactly(java.util.Set.of("x"));
        verify(store).skip(OfferSite.MINDQUEST, List.of("0b"), NOW);
        verify(runs).succeeded(OfferSite.MINDQUEST, NOW, 1, 0);
    }

    @Test
    void recordsAFailedRunWithoutThrowing() {
        OfferReader broken = new PagedReader(OfferSite.YEETS, true, 1) {
            @Override
            public void read(PageFetcher fetcher, OfferBatch batch) throws IOException {
                throw new SiteResponseException(503, "GET /api answered 503");
            }
        };

        OfferCollector.RunResult result = collector(broken).collect(OfferSite.YEETS);

        assertThat(result.pages()).isZero();
        verify(runs).failed(eq(OfferSite.YEETS), eq(NOW), contains("503"));
        verify(runs, never()).succeeded(any(), any(), anyInt(), anyInt());
    }

    @Test
    void recordsAnInterruptedRun() {
        OfferReader interrupted = new PagedReader(OfferSite.YEETS, true, 1) {
            @Override
            public void read(PageFetcher fetcher, OfferBatch batch) throws InterruptedException {
                throw new InterruptedException();
            }
        };

        collector(interrupted).collect(OfferSite.YEETS);

        assertThat(Thread.interrupted()).isTrue();
        verify(runs).failed(OfferSite.YEETS, NOW, "Interrupted");
    }

    @Test
    void readsOnlyTheSitesItWins() {
        when(runs.claim(OfferSite.LEHIBOU, NOW)).thenReturn(true);
        when(runs.claim(OfferSite.YEETS, NOW)).thenReturn(false);
        PagedReader lehibou = new PagedReader(OfferSite.LEHIBOU, true, 0);
        PagedReader yeets = new PagedReader(OfferSite.YEETS, true, 0);

        new OfferCollector(List.of(lehibou, yeets), http, normalizer, store, runs, properties, Clock.fixed(NOW, ZoneOffset.UTC))
            .collectDueSites();

        assertThat(lehibou.calls).isEqualTo(1);
        assertThat(yeets.calls).isZero();
    }

    @Test
    void readsNothingOnceInterrupted() {
        PagedReader reader = new PagedReader(OfferSite.LEHIBOU, true, 0);
        Thread.currentThread().interrupt();
        try {
            collector(reader).collectDueSites();
        } finally {
            Thread.interrupted();
        }

        verify(runs, never()).claim(any(), any());
    }

    @Test
    void readsOneOfferFromItsLinkAndStoresItAsARunWould() {
        JobOffer stored = new JobOffer();
        when(normalizer.normalize(eq(OfferSite.MINDQUEST), any(), eq(NOW))).thenReturn(Optional.of(stored), Optional.empty());
        OneOfferReader reader = new OneOfferReader(ScrapedOffer.builder().externalId("1").title("Architecte").freelance(true).build());
        OfferCollector collector = collector(reader);

        assertThat(collector.offerId(OfferSite.MINDQUEST, "https://example.com/1")).contains("1");
        assertThat(collector.offerId(OfferSite.YEETS, "https://example.com/1")).isEmpty();
        assertThat(collector.readOne(OfferSite.MINDQUEST, "https://example.com/1")).isEqualTo(OfferLinkStatus.FOUND);
        verify(store).save(OfferSite.MINDQUEST, List.of(stored));
        // The same offer, now past its expiry date.
        assertThat(collector.readOne(OfferSite.MINDQUEST, "https://example.com/1")).isEqualTo(OfferLinkStatus.EXPIRED);
        verify(store).skip(OfferSite.MINDQUEST, List.of("1"), NOW);
    }

    @Test
    void tellsWhyAnOfferReadFromItsLinkStaysOut() {
        when(normalizer.normalize(eq(OfferSite.MINDQUEST), any(), any())).thenReturn(Optional.empty());

        assertThat(collector(new OneOfferReader(ScrapedOffer.builder().externalId("1").freelance(false).build()))
            .readOne(OfferSite.MINDQUEST, "https://example.com/1")).isEqualTo(OfferLinkStatus.GONE);
        assertThat(collector(new OneOfferReader(ScrapedOffer.builder().externalId("1").title("Architecte").freelance(false).build()))
            .readOne(OfferSite.MINDQUEST, "https://example.com/1")).isEqualTo(OfferLinkStatus.NOT_FREELANCE);
        assertThat(collector(new OneOfferReader(ScrapedOffer.builder().externalId("1").title("Architecte (CDI)").freelance(true).build()))
            .readOne(OfferSite.MINDQUEST, "https://example.com/1")).isEqualTo(OfferLinkStatus.NOT_FREELANCE);
        assertThat(collector(new OneOfferReader(null)).readOne(OfferSite.MINDQUEST, "https://example.com/1")).isEqualTo(OfferLinkStatus.GONE);
    }

    @Test
    void tellsASiteThatCouldNotBeReadForOneOffer() {
        OneOfferReader failing = new OneOfferReader(null) {
            @Override
            public Optional<ScrapedOffer> readOne(PageFetcher fetcher, String url) throws IOException {
                throw new SiteResponseException(0, "robots.txt disallows /1");
            }
        };
        OneOfferReader interrupted = new OneOfferReader(null) {
            @Override
            public Optional<ScrapedOffer> readOne(PageFetcher fetcher, String url) throws InterruptedException {
                throw new InterruptedException();
            }
        };

        assertThat(collector(failing).readOne(OfferSite.MINDQUEST, "https://example.com/1")).isEqualTo(OfferLinkStatus.SITE_UNAVAILABLE);
        assertThat(collector(interrupted).readOne(OfferSite.MINDQUEST, "https://example.com/1")).isEqualTo(OfferLinkStatus.SITE_UNAVAILABLE);
        assertThat(Thread.interrupted()).isTrue();
        verify(store, never()).save(any(), anyList());
    }

    private OfferCollector collector(OfferReader reader) {
        return new OfferCollector(List.of(reader), http, normalizer, store, runs, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** A site whose offers are read one by one: {@code offer} for any link, none when it is {@code null}. */
    private static class OneOfferReader extends PagedReader {
        private final ScrapedOffer offer;

        OneOfferReader(ScrapedOffer offer) {
            super(OfferSite.MINDQUEST, false, 0);
            this.offer = offer;
        }

        @Override
        public Optional<String> offerId(String url) {
            return Optional.of(url.substring(url.lastIndexOf('/') + 1));
        }

        @Override
        public Optional<ScrapedOffer> readOne(PageFetcher fetcher, String url) throws IOException, InterruptedException {
            return Optional.ofNullable(offer);
        }
    }

    /** Hands {@code pages} pages of two offers each, as long as the batch asks for more. */
    private static class PagedReader implements OfferReader {
        private final OfferSite site;
        private final boolean newestFirst;
        private final int pages;
        int pagesRead;
        int calls;

        PagedReader(OfferSite site, boolean newestFirst, int pages) {
            this.site = site;
            this.newestFirst = newestFirst;
            this.pages = pages;
        }

        @Override
        public OfferSite site() {
            return site;
        }

        @Override
        public String domain() {
            return "example.com";
        }

        @Override
        public boolean newestFirst() {
            return newestFirst;
        }

        @Override
        public void read(PageFetcher fetcher, OfferBatch batch) throws IOException, InterruptedException {
            calls++;
            for (int page = 0; page < pages; page++) {
                pagesRead++;
                List<ScrapedOffer> offers = new ArrayList<>();
                offers.add(ScrapedOffer.builder().externalId(page + "a").build());
                offers.add(ScrapedOffer.builder().externalId(page + "b").build());
                if (!batch.add(offers)) {
                    return;
                }
            }
        }
    }
}
