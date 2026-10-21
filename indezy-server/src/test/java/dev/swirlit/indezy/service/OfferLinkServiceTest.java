package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferDraftDto;
import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.dto.OfferLinkDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferLinkStatus;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.OfferLinkReader;
import dev.swirlit.indezy.scraping.OfferNormalizer;
import dev.swirlit.indezy.scraping.PostingReader;
import dev.swirlit.indezy.scraping.ScrapedOffer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final String LINK = "https://www.lehibou.com/annonce/42";

    private final OfferLinkReader reader = mock(OfferLinkReader.class);
    private final OfferStateService offerStates = mock(OfferStateService.class);
    private final OfferImportService importService = mock(OfferImportService.class);
    private final OfferSearchService search = mock(OfferSearchService.class);
    private final PostingReader postingReader = mock(PostingReader.class);
    private final OfferNormalizer normalizer = mock(OfferNormalizer.class);
    private final MovableClock clock = new MovableClock();
    private final OfferLinkService service = new OfferLinkService(reader, offerStates, importService, search,
        new OfferLinkService.Postings(postingReader, normalizer), clock);

    @Test
    void draftsTheOpportunityOfAPostingOnAnotherSiteWithoutStoringIt() throws Exception {
        String link = "https://www.welcometothejungle.com/fr/companies/acme/jobs/developpeur-java";
        when(reader.siteOf(any())).thenReturn(Optional.empty());
        when(importService.postingProject(3L, link)).thenReturn(Optional.empty());
        ScrapedOffer scraped = ScrapedOffer.builder().title("Développeur Java").url(link).build();
        when(postingReader.read(URI.create(link))).thenReturn(new PostingReader.Reading(PostingReader.Outcome.FOUND, scraped));
        JobOffer posting = new JobOffer();
        when(normalizer.posting(scraped, NOW)).thenReturn(Optional.of(posting));
        PostingSite jungle = new PostingSite("Welcome to the Jungle", "https://www.welcometothejungle.com");
        OfferDraftDto draft = new OfferDraftDto(new ProjectDto(), "Acme", jungle.name(), jungle.homeUrl());
        when(importService.draftPosting(3L, posting, jungle)).thenReturn(draft);

        assertThat(service.resolve(3L, link))
            .isEqualTo(new OfferLinkDto(OfferLinkStatus.POSTING, "Welcome to the Jungle", null, null, draft, null));
        verify(reader, never()).find(any(), any(), any());
    }

    @Test
    void tellsWhatTheLinkOfAPostingGaveWhenItGaveNoDraft() throws Exception {
        String link = "https://jobs.example.org/postes/42";
        when(reader.siteOf(any())).thenReturn(Optional.empty());
        when(importService.postingProject(eq(3L), any())).thenReturn(Optional.empty());
        ScrapedOffer permanent = ScrapedOffer.builder().title("Comptable (CDI)").build();
        Map<PostingReader.Reading, OfferLinkStatus> expected = new LinkedHashMap<>();
        expected.put(new PostingReader.Reading(PostingReader.Outcome.NOT_FREELANCE, permanent), OfferLinkStatus.NOT_FREELANCE);
        expected.put(new PostingReader.Reading(PostingReader.Outcome.NONE, null), OfferLinkStatus.GONE);
        expected.put(new PostingReader.Reading(PostingReader.Outcome.UNAVAILABLE, null), OfferLinkStatus.SITE_UNAVAILABLE);
        ScrapedOffer untitled = ScrapedOffer.builder().build();
        expected.put(new PostingReader.Reading(PostingReader.Outcome.FOUND, untitled), OfferLinkStatus.GONE);
        when(normalizer.posting(untitled, NOW)).thenReturn(Optional.empty());

        for (Map.Entry<PostingReader.Reading, OfferLinkStatus> outcome : expected.entrySet()) {
            when(postingReader.read(any())).thenReturn(outcome.getKey());
            assertThat(service.resolve(3L, link))
                .isEqualTo(new OfferLinkDto(outcome.getValue(), "jobs.example.org", null, null, null, null));
        }
        verify(importService, never()).draftPosting(any(), any(), any());
    }

    @Test
    void opensTheOpportunityTheLinkOfAPostingMadeAlready() throws Exception {
        String link = "https://jobs.example.org/postes/42";
        when(reader.siteOf(any())).thenReturn(Optional.empty());
        when(importService.postingProject(3L, link)).thenReturn(Optional.of(12L));

        assertThat(service.resolve(3L, link))
            .isEqualTo(new OfferLinkDto(OfferLinkStatus.IN_PIPELINE, "jobs.example.org", null, 12L, null, null));
        verify(postingReader, never()).read(any());
    }

    @Test
    void draftsTheOpportunityOfAnOfferFound() {
        JobOffer offer = foundOffer();
        OfferDto dto = mock(OfferDto.class);
        OfferDraftDto draft = new OfferDraftDto(new ProjectDto(), "Exemple Conseil", "LeHibou", null);
        when(search.get(3L, offer.getId())).thenReturn(dto);
        when(offerStates.pipelineProject(3L, offer.getId())).thenReturn(Optional.empty());
        when(importService.draft(3L, offer.getId())).thenReturn(draft);

        assertThat(service.resolve(3L, LINK))
            .isEqualTo(new OfferLinkDto(OfferLinkStatus.FOUND, "LeHibou", dto, null, draft, null));
    }

    @Test
    void opensTheOpportunityOfAMissionAlreadyInThePipeline() {
        JobOffer offer = foundOffer();
        OfferDto dto = mock(OfferDto.class);
        when(search.get(3L, offer.getId())).thenReturn(dto);
        when(offerStates.pipelineProject(3L, offer.getId())).thenReturn(Optional.of(40L));

        assertThat(service.resolve(3L, LINK))
            .isEqualTo(new OfferLinkDto(OfferLinkStatus.IN_PIPELINE, "LeHibou", dto, 40L, null, null));
        verify(importService, never()).draft(any(), any());
    }

    @Test
    void tellsWhatTheSiteSaidOfAnOfferNotFound() {
        when(reader.siteOf(URI.create(LINK))).thenReturn(Optional.of(OfferSite.LEHIBOU));
        when(reader.find(eq(OfferSite.LEHIBOU), eq(URI.create(LINK)), any()))
            .thenReturn(new OfferLinkReader.Lookup(OfferLinkStatus.READING, null, NOW.plusSeconds(5)));

        assertThat(service.resolve(3L, LINK + "/?utm_source=alerte"))
            .isEqualTo(new OfferLinkDto(OfferLinkStatus.READING, "LeHibou", null, null, null, NOW.plusSeconds(5)));
    }

    @Test
    void readsAtMostTwentyPagesAnHourForAWorkspace() {
        when(reader.siteOf(any())).thenReturn(Optional.of(OfferSite.LEHIBOU));
        when(reader.find(any(), any(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return new OfferLinkReader.Lookup(OfferLinkStatus.GONE, null, null);
        });
        for (int read = 0; read < OfferLinkService.READS_PER_WINDOW; read++) {
            service.resolve(3L, LINK);
        }

        assertThatThrownBy(() -> service.resolve(3L, LINK)).isInstanceOf(TooManyRequestsException.class)
            .extracting(error -> ((TooManyRequestsException) error).getHeaders().getFirst("Retry-After")).isEqualTo("3600");
        // Another workspace reads its own share, and an hour later the first one can read again.
        assertThat(service.resolve(4L, LINK).status()).isEqualTo(OfferLinkStatus.GONE);
        clock.now = NOW.plus(OfferLinkService.READ_WINDOW);
        assertThat(service.resolve(3L, LINK).status()).isEqualTo(OfferLinkStatus.GONE);
    }

    private JobOffer foundOffer() {
        JobOffer offer = new JobOffer();
        offer.setId(42L);
        when(reader.siteOf(URI.create(LINK))).thenReturn(Optional.of(OfferSite.LEHIBOU));
        when(reader.find(eq(OfferSite.LEHIBOU), eq(URI.create(LINK)), any()))
            .thenReturn(new OfferLinkReader.Lookup(OfferLinkStatus.FOUND, offer.getId(), null));
        return offer;
    }

    /** A clock the test moves forward. */
    private static final class MovableClock extends Clock {
        private Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
