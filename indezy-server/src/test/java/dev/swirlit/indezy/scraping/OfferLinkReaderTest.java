package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferSiteStatus;
import dev.swirlit.indezy.model.enums.OfferLinkStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferSiteStatusRepository;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferLinkReaderTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final String MISSION = "https://www.free-work.com/fr/tech-it/developpeur-java/job-mission/dev-java-spring";

    private final OfferCollector collector = mock(OfferCollector.class);
    private final OfferSiteRefresher refresher = mock(OfferSiteRefresher.class);
    private final OfferSiteStatusRepository statuses = mock(OfferSiteStatusRepository.class);
    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final List<OfferReader> readers = List.of(reader(OfferSite.LEHIBOU, "lehibou.com"), reader(OfferSite.FREE_WORK, "free-work.com"),
        reader(OfferSite.CHERRY_PICK, "cherry-pick.io"));

    @Test
    void readsALinkWithoutItsFragmentTrackingAndTrailingSlashes() {
        assertThat(OfferLinkReader.parse(" www.free-work.com/fr/mission/dev-java/?utm_source=alerte&gclid=1#postuler "))
            .isEqualTo(URI.create("https://www.free-work.com/fr/mission/dev-java"));
        assertThat(OfferLinkReader.parse("http://WWW.Example.com/offre?id=5&utm_medium=mail&ref=home"))
            .isEqualTo(URI.create("https://www.example.com/offre?id=5"));
        assertThat(OfferLinkReader.parse("https://www.example.com")).isEqualTo(URI.create("https://www.example.com"));
    }

    @Test
    void refusesWhatIsNoWebAddress() {
        for (String link : new String[] {"", "  ", "ftp://example.com/offre", "https://moi:secret@example.com/offre",
            "https://example.com:8443/offre", "javascript:alert(1)", "https://exa mple.com/offre", "https://example.com/" + "a".repeat(2_000)}) {
            assertThatThrownBy(() -> OfferLinkReader.parse(link)).as(link).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> OfferLinkReader.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void knowsTheSitesTheFeedReadsSubdomainsIncluded() {
        OfferLinkReader linkReader = linkReader(true);

        assertThat(linkReader.siteOf(URI.create("https://api.lehibou.com/annonce/1"))).contains(OfferSite.LEHIBOU);
        assertThat(linkReader.siteOf(URI.create("https://free-work.com/fr"))).contains(OfferSite.FREE_WORK);
        assertThat(linkReader.siteOf(URI.create("https://notlehibou.com/annonce/1"))).isEmpty();
    }

    @Test
    void findsACollectedOfferByItsIdentifierFirst() {
        JobOffer offer = offer(5L, MISSION);
        when(collector.offerId(OfferSite.FREE_WORK, MISSION)).thenReturn(Optional.of("dev-java-spring"));
        when(offers.findBySiteAndExternalIdIn(OfferSite.FREE_WORK, List.of("dev-java-spring"))).thenReturn(List.of(offer));

        OfferLinkReader.Lookup lookup = linkReader(true).find(OfferSite.FREE_WORK, URI.create(MISSION), () -> { });

        assertThat(lookup).isEqualTo(new OfferLinkReader.Lookup(OfferLinkStatus.FOUND, 5L, null));
        verify(collector, never()).readOne(any(), anyString());
    }

    @Test
    void findsACollectedOfferWhoseAddressEndsAlikeTheSamePathFirst() {
        JobOffer english = offer(9L, "https://www.free-work.com/en/tech-it/java-developer/job-mission/dev-java-spring");
        JobOffer same = offer(4L, MISSION + "/");
        JobOffer longer = offer(8L, MISSION + "-senior");
        when(offers.findTop20BySiteAndUrlContainingIgnoreCaseOrderByIdDesc(OfferSite.FREE_WORK, "/dev-java-spring"))
            .thenReturn(List.of(english, longer, same));

        assertThat(linkReader(true).stored(OfferSite.FREE_WORK, URI.create(MISSION))).contains(same);
        assertThat(linkReader(true).stored(OfferSite.FREE_WORK,
            URI.create("https://www.free-work.com/fr/tech-it/job-mission/dev-java-spring"))).contains(english);
        assertThat(linkReader(true).stored(OfferSite.FREE_WORK, URI.create("https://www.free-work.com"))).isEmpty();
    }

    @Test
    void matchesAnAddressWrittenWithOrWithoutItsEncodedLetters() {
        JobOffer offer = offer(3L, "https://www.lehibou.com/annonce/d%C3%A9veloppeur");
        // The database compares the addresses case aside.
        when(offers.findTop20BySiteAndUrlContainingIgnoreCaseOrderByIdDesc(OfferSite.LEHIBOU, "/D%C3%A9veloppeur")).thenReturn(List.of(offer));

        assertThat(linkReader(true).stored(OfferSite.LEHIBOU, URI.create("https://www.lehibou.com/annonce/D%C3%A9veloppeur"))).contains(offer);
        assertThat(OfferLinkReader.lastSegment("https://www.lehibou.com/annonce/d%C3%A9veloppeur?x=1#top")).isEqualTo("développeur");
        assertThat(OfferLinkReader.lastSegment("https://www.lehibou.com/annonce/Bad%E")).isEqualTo("bad%e");
        assertThat(OfferLinkReader.lastSegment("https://www.lehibou.com")).isNull();
    }

    @Test
    void namesAnOfferByTheParameterItsSiteNamesItBy() {
        JobOffer five = offer(5L, "https://www.cherry-pick.io/offre?id=5&utm_source=feed");
        when(offers.findTop20BySiteAndUrlContainingIgnoreCaseOrderByIdDesc(OfferSite.CHERRY_PICK, "/offre")).thenReturn(List.of(five));

        assertThat(linkReader(true).stored(OfferSite.CHERRY_PICK, OfferLinkReader.parse("https://www.cherry-pick.io/offre?id=5"))).contains(five);
        assertThat(linkReader(true).stored(OfferSite.CHERRY_PICK, OfferLinkReader.parse("https://www.cherry-pick.io/offre?id=6"))).isEmpty();
    }

    @Test
    void readsAnOfferNotCollectedYetFromItsPage() {
        URI link = URI.create("https://www.lehibou.com/annonce/42");
        JobOffer offer = offer(42L, link.toString());
        when(collector.offerId(OfferSite.LEHIBOU, link.toString())).thenReturn(Optional.of("42"));
        when(offers.findBySiteAndExternalIdIn(OfferSite.LEHIBOU, List.of("42"))).thenReturn(List.of(), List.of(offer));
        when(collector.readOne(OfferSite.LEHIBOU, link.toString())).thenReturn(OfferLinkStatus.FOUND);
        AtomicInteger counted = new AtomicInteger();

        OfferLinkReader.Lookup lookup = linkReader(true).find(OfferSite.LEHIBOU, link, counted::incrementAndGet);

        assertThat(lookup).isEqualTo(new OfferLinkReader.Lookup(OfferLinkStatus.FOUND, 42L, null));
        assertThat(counted).hasValue(1);
    }

    @Test
    void tellsWhyAnOfferReadFromItsPageIsNotInTheFeed() {
        URI link = URI.create("https://www.lehibou.com/annonce/42");
        when(collector.offerId(OfferSite.LEHIBOU, link.toString())).thenReturn(Optional.of("42"));
        when(collector.readOne(OfferSite.LEHIBOU, link.toString())).thenReturn(OfferLinkStatus.NOT_FREELANCE, OfferLinkStatus.FOUND);

        assertThat(linkReader(true).find(OfferSite.LEHIBOU, link, () -> { }).status()).isEqualTo(OfferLinkStatus.NOT_FREELANCE);
        // Stored, then removed before it could be found again.
        assertThat(linkReader(true).find(OfferSite.LEHIBOU, link, () -> { }).status()).isEqualTo(OfferLinkStatus.GONE);
    }

    @Test
    void readsNoPageOnceTheWorkspaceReadItsShare() {
        URI link = URI.create("https://www.lehibou.com/annonce/42");
        when(collector.offerId(OfferSite.LEHIBOU, link.toString())).thenReturn(Optional.of("42"));

        assertThatThrownBy(() -> linkReader(true).find(OfferSite.LEHIBOU, link, () -> {
            throw new TooManyRequestsException("too many", Duration.ofMinutes(5));
        })).isInstanceOf(TooManyRequestsException.class);
        verify(collector, never()).readOne(any(), anyString());
    }

    @Test
    void readsNothingWhileCollectionIsOff() {
        assertThat(linkReader(false).find(OfferSite.FREE_WORK, URI.create(MISSION), () -> { }))
            .isEqualTo(new OfferLinkReader.Lookup(OfferLinkStatus.NOT_COLLECTED, null, null));
        verify(refresher, never()).refresh(any());
    }

    @Test
    void asksForAReadingOfASiteWhoseOffersAreNotReadOneByOne() {
        OfferLinkReader.Lookup lookup = linkReader(true).find(OfferSite.FREE_WORK, URI.create(MISSION), () -> { });

        assertThat(lookup).isEqualTo(new OfferLinkReader.Lookup(OfferLinkStatus.READING, null, NOW.plus(OfferLinkReader.LOOK_AGAIN_AFTER)));
        verify(refresher).refresh(OfferSite.FREE_WORK);
    }

    @Test
    void waitsForAReadingUnderWayAndTellsWhenTheSiteCanBeReadAgain() {
        when(refresher.refresh(OfferSite.FREE_WORK)).thenThrow(new TooManyRequestsException("too soon", Duration.ofMinutes(10)));
        OfferSiteStatus status = new OfferSiteStatus(OfferSite.FREE_WORK, NOW);
        when(statuses.findById(OfferSite.FREE_WORK)).thenReturn(Optional.of(status));
        URI link = URI.create(MISSION);

        status.setRunningSince(NOW.minusSeconds(30));
        assertThat(linkReader(true).find(OfferSite.FREE_WORK, link, () -> { }).status()).isEqualTo(OfferLinkStatus.READING);

        status.setRunningSince(null);
        status.setLastRunAt(NOW.minusSeconds(120));
        status.setRefreshRequestedAt(NOW.minusSeconds(60));
        assertThat(linkReader(true).find(OfferSite.FREE_WORK, link, () -> { }).status()).isEqualTo(OfferLinkStatus.READING);

        // The reading asked for ended without the offer; a run that never ended no longer counts.
        status.setRunningSince(NOW.minus(Duration.ofHours(2)));
        status.setLastRunAt(NOW.minusSeconds(30));
        assertThat(linkReader(true).find(OfferSite.FREE_WORK, link, () -> { }))
            .isEqualTo(new OfferLinkReader.Lookup(OfferLinkStatus.NOT_COLLECTED, null, NOW.minusSeconds(30).plus(OfferSiteRuns.REFRESH_EVERY)));

        when(statuses.findById(OfferSite.FREE_WORK)).thenReturn(Optional.empty());
        assertThat(linkReader(true).find(OfferSite.FREE_WORK, link, () -> { }))
            .isEqualTo(new OfferLinkReader.Lookup(OfferLinkStatus.NOT_COLLECTED, null, null));
    }

    private OfferLinkReader linkReader(boolean collectionEnabled) {
        OfferProperties properties = new OfferProperties(collectionEnabled, Duration.ofMinutes(5), Duration.ofSeconds(2), 3,
            Duration.ofHours(1), 90, "IndezyBot/test", false, "http://127.0.0.1", "http://127.0.0.1:9/geocodage");
        return new OfferLinkReader(readers, collector, refresher, statuses, properties, offers, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static JobOffer offer(Long id, String url) {
        JobOffer offer = new JobOffer();
        offer.setId(id);
        offer.setUrl(url);
        return offer;
    }

    private static OfferReader reader(OfferSite site, String domain) {
        return new OfferReader() {
            @Override
            public OfferSite site() {
                return site;
            }

            @Override
            public String domain() {
                return domain;
            }

            @Override
            public void read(PageFetcher fetcher, OfferBatch batch) {
                // Not read here.
            }
        };
    }
}
