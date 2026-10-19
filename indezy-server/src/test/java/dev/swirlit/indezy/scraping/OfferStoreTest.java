package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferDatePrecision;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.JobOfferRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OfferStoreTest {

    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final OfferStore store = new OfferStore(offers);

    @Test
    @SuppressWarnings("unchecked")
    void addsNewOffersAndRefreshesKnownOnes() {
        JobOffer known = offer("1", "Ancien titre", OfferDatePrecision.FIRST_SEEN, Instant.parse("2026-10-01T00:00:00Z"));
        known.setSkills(List.of("Java"));
        when(offers.findBySiteAndExternalIdIn(eq(OfferSite.FREE_WORK), any())).thenReturn(List.of(known));
        JobOffer refreshed = offer("1", "Nouveau titre", OfferDatePrecision.FIRST_SEEN, Instant.parse("2026-10-19T00:00:00Z"));
        refreshed.setSkills(List.of("Java", "Kafka"));
        JobOffer created = offer("2", "Data engineer", OfferDatePrecision.EXACT, Instant.parse("2026-10-19T08:00:00Z"));

        int added = store.save(OfferSite.FREE_WORK, List.of(refreshed, created, offer("2", "Doublon", OfferDatePrecision.EXACT, null)));

        assertThat(added).isEqualTo(1);
        assertThat(known.getTitle()).isEqualTo("Nouveau titre");
        assertThat(known.getSkills()).containsExactly("Java", "Kafka");
        assertThat(known.getPublishedAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        ArgumentCaptor<List<JobOffer>> saved = ArgumentCaptor.forClass(List.class);
        verify(offers).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(JobOffer::getTitle).containsExactly("Data engineer");
    }

    @Test
    void movesTheDateOfARepublishedOffer() {
        JobOffer known = offer("1", "Titre", OfferDatePrecision.EXACT, Instant.parse("2026-10-01T00:00:00Z"));
        when(offers.findBySiteAndExternalIdIn(eq(OfferSite.FREE_WORK), any())).thenReturn(List.of(known));

        store.save(OfferSite.FREE_WORK, List.of(offer("1", "Titre", OfferDatePrecision.EXACT, Instant.parse("2026-10-19T00:00:00Z"))));

        assertThat(known.getPublishedAt()).isEqualTo(Instant.parse("2026-10-19T00:00:00Z"));
    }

    @Test
    void savesNothingForAnEmptyPage() {
        assertThat(store.save(OfferSite.YEETS, List.of())).isZero();
        verifyNoInteractions(offers);
    }

    private static JobOffer offer(String externalId, String title, OfferDatePrecision precision, Instant publishedAt) {
        JobOffer offer = new JobOffer();
        offer.setSite(OfferSite.FREE_WORK);
        offer.setExternalId(externalId);
        offer.setUrl("https://www.free-work.com/" + externalId);
        offer.setTitle(title);
        offer.setDatePrecision(precision);
        offer.setPublishedAt(publishedAt);
        offer.setFirstSeenAt(publishedAt);
        offer.setLastSeenAt(publishedAt);
        return offer;
    }
}
