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
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
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
    private final MovableClock clock = new MovableClock();
    private final OfferLinkService service = new OfferLinkService(reader, offerStates, importService, search, clock);

    @Test
    void namesTheSupportedSitesForALinkOnAnotherSite() {
        when(reader.siteOf(any())).thenReturn(Optional.empty());
        when(reader.siteNames()).thenReturn(List.of("Free-Work", "LeHibou"));

        OfferLinkDto link = service.resolve(3L, "https://www.example.com/offre/1");

        assertThat(link).isEqualTo(new OfferLinkDto(OfferLinkStatus.UNSUPPORTED_SITE, null, null, null, null, null, List.of("Free-Work", "LeHibou")));
        verify(reader, never()).find(any(), any(), any());
    }

    @Test
    void draftsTheOpportunityOfAnOfferFound() {
        JobOffer offer = foundOffer();
        OfferDto dto = mock(OfferDto.class);
        OfferDraftDto draft = new OfferDraftDto(new ProjectDto(), "Exemple Conseil", "LeHibou");
        when(search.get(3L, offer.getId())).thenReturn(dto);
        when(offerStates.pipelineProject(3L, offer.getId())).thenReturn(Optional.empty());
        when(importService.draft(3L, offer.getId())).thenReturn(draft);

        assertThat(service.resolve(3L, LINK))
            .isEqualTo(new OfferLinkDto(OfferLinkStatus.FOUND, "LeHibou", dto, null, draft, null, null));
    }

    @Test
    void opensTheOpportunityOfAMissionAlreadyInThePipeline() {
        JobOffer offer = foundOffer();
        OfferDto dto = mock(OfferDto.class);
        when(search.get(3L, offer.getId())).thenReturn(dto);
        when(offerStates.pipelineProject(3L, offer.getId())).thenReturn(Optional.of(40L));

        assertThat(service.resolve(3L, LINK))
            .isEqualTo(new OfferLinkDto(OfferLinkStatus.IN_PIPELINE, "LeHibou", dto, 40L, null, null, null));
        verify(importService, never()).draft(any(), any());
    }

    @Test
    void tellsWhatTheSiteSaidOfAnOfferNotFound() {
        when(reader.siteOf(URI.create(LINK))).thenReturn(Optional.of(OfferSite.LEHIBOU));
        when(reader.find(eq(OfferSite.LEHIBOU), eq(URI.create(LINK)), any()))
            .thenReturn(new OfferLinkReader.Lookup(OfferLinkStatus.READING, null, NOW.plusSeconds(5)));

        assertThat(service.resolve(3L, LINK + "/?utm_source=alerte"))
            .isEqualTo(new OfferLinkDto(OfferLinkStatus.READING, "LeHibou", null, null, null, NOW.plusSeconds(5), null));
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
