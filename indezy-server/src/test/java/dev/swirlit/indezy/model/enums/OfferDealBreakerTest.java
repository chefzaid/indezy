package dev.swirlit.indezy.model.enums;

import dev.swirlit.indezy.dto.OfferMatchDto;
import dev.swirlit.indezy.model.JobOffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfferDealBreakerTest {

    private static JobOffer offer(WorkMode mode, String description) {
        JobOffer offer = new JobOffer();
        offer.setTitle("Développeur Java");
        offer.setWorkMode(mode);
        offer.setDescription(description);
        return offer;
    }

    @Test
    void readsTheDealBreakersFromTheOfferAndItsMatch() {
        OfferMatchDto poor = new OfferMatchDto(20, List.of(), OfferMatchDto.RateFit.BELOW, OfferMatchDto.PlaceFit.FAR,
            OfferMatchDto.AvailabilityFit.TOO_EARLY);
        assertThat(OfferDealBreaker.of(offer(WorkMode.ONSITE, "Mission en portage salarial obligatoire. Poste en CDI à terme."), poor))
            .containsExactly(OfferDealBreaker.ON_SITE_ONLY, OfferDealBreaker.RATE_UNDER_TARGET, OfferDealBreaker.TOO_FAR,
                OfferDealBreaker.STARTS_TOO_EARLY, OfferDealBreaker.PORTAGE_REQUIRED, OfferDealBreaker.PERMANENT_JOB);
        assertThat(OfferDealBreaker.of(offer(WorkMode.HYBRID, "Freelance only through portage. We hire a permanent position only."), null))
            .containsExactly(OfferDealBreaker.PORTAGE_REQUIRED, OfferDealBreaker.PERMANENT_JOB);
        assertThat(OfferDealBreaker.of(offer(WorkMode.HYBRID, "Pas de freelance : nous recrutons."), null))
            .containsExactly(OfferDealBreaker.PORTAGE_REQUIRED);
    }

    @Test
    void findsNothingInAnOrdinaryMission() {
        OfferMatchDto good = new OfferMatchDto(90, List.of("Java"), OfferMatchDto.RateFit.MEETS, OfferMatchDto.PlaceFit.NEAR,
            OfferMatchDto.AvailabilityFit.READY);
        assertThat(OfferDealBreaker.of(offer(WorkMode.HYBRID, "Mission freelance, portage possible, CDI/Freelance accepté, 2 jours de " +
            "télétravail."), good)).isEmpty();
        assertThat(OfferDealBreaker.of(offer(null, null), null)).isEmpty();
    }
}
