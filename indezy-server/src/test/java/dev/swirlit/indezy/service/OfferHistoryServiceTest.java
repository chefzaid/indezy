package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferHistoryDto;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferChange;
import dev.swirlit.indezy.model.enums.OfferChangeField;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.OfferAppearanceRepository;
import dev.swirlit.indezy.repository.OfferChangeRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferHistoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    private final OfferChangeRepository changes = mock(OfferChangeRepository.class);
    private final OfferAppearanceRepository appearances = mock(OfferAppearanceRepository.class);
    private final OfferHistoryService service = new OfferHistoryService(changes, appearances);

    @Test
    void tellsWhatChangedAndHowOftenEachMissionCameBack() {
        JobOffer java = offer(1L, OfferSite.FREE_WORK, "java");
        JobOffer cloud = offer(2L, OfferSite.COLLECTIVE, "cloud");
        JobOffer quiet = offer(3L, OfferSite.FREE_WORK, null);
        List<OfferChange> stored = new ArrayList<>();
        IntStream.range(0, 12).forEach(index -> stored.add(change(java, OfferChangeField.RATE, "|" + (600 - index * 10), "|" + (610 - index * 10))));
        stored.add(change(cloud, OfferChangeField.START, null, "ASAP"));
        when(changes.findByOfferIds(List.of(1L, 2L, 3L))).thenReturn(stored);
        // Java came under three references on Free-Work; cloud once on Collective, and twice on another site that does not count.
        when(appearances.countSince(List.of("java", "cloud"), NOW.minus(OfferHistoryService.REPUBLISHED_WITHIN)))
            .thenReturn(List.of(new Object[] {OfferSite.FREE_WORK, "java", 3L}, new Object[] {OfferSite.COLLECTIVE, "cloud", 1L},
                new Object[] {OfferSite.YEETS, "cloud", 2L}));

        Map<Long, OfferHistoryDto> histories = service.of(List.of(java, cloud, quiet), NOW);

        assertThat(histories.get(1L).publishedTimes()).isEqualTo(3);
        assertThat(histories.get(1L).changes()).hasSize(OfferHistoryService.MAX_CHANGES)
            .first().isEqualTo(new OfferHistoryDto.Change(OfferChangeField.RATE, "|600", "|610", NOW));
        assertThat(histories.get(2L)).isEqualTo(new OfferHistoryDto(null, List.of(new OfferHistoryDto.Change(OfferChangeField.START, null,
            "ASAP", NOW))));
        assertThat(histories).doesNotContainKey(3L);
    }

    @Test
    void readsNothingForNoOffer() {
        assertThat(service.of(List.of(), NOW)).isEmpty();
        verify(changes, never()).findByOfferIds(any());

        assertThat(service.of(List.of(offer(3L, OfferSite.FREE_WORK, null)), NOW)).isEmpty();
        verify(appearances, never()).countSince(any(), any());
    }

    private static JobOffer offer(Long id, OfferSite site, String publisherKey) {
        JobOffer offer = new JobOffer();
        offer.setId(id);
        offer.setSite(site);
        offer.setPublisherKey(publisherKey);
        return offer;
    }

    private static OfferChange change(JobOffer offer, OfferChangeField field, String before, String after) {
        OfferChange change = new OfferChange();
        change.setOffer(offer);
        change.setField(field);
        change.setBefore(before);
        change.setAfter(after);
        change.setChangedAt(NOW);
        return change;
    }
}
