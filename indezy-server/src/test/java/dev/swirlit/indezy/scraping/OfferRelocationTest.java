package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.repository.JobOfferRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferRelocationTest {

    private final JobOfferRepository offers = mock(JobOfferRepository.class);
    private final OfferNormalizer normalizer = mock(OfferNormalizer.class);
    private final OfferRelocation relocation = new OfferRelocation(offers, normalizer);

    @Test
    void looksTheUnlocatedOffersUpAgainBatchAfterBatch() {
        JobOffer first = offer(4L);
        JobOffer second = offer(9L);
        when(offers.findUnlocatedAfter(eq(0L), any())).thenReturn(List.of(first, second));

        assertThat(relocation.locateStoredOffers(0L)).isEqualTo(9L);
        verify(normalizer).relocate(first);
        verify(normalizer).relocate(second);
        assertThat(relocation.locateStoredOffers(9L)).isNull();
    }

    private static JobOffer offer(long id) {
        JobOffer offer = new JobOffer();
        offer.setId(id);
        return offer;
    }
}
