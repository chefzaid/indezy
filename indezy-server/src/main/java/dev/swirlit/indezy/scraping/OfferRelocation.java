package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Locates the stored offers that name a place and have no coordinates: a lookup that timed out is not remembered, and
 * the sites read page by page never read a stored offer again, so nothing else would ask once more. Lookups answered
 * before come from the geocoding cache.
 */
@Service
@RequiredArgsConstructor
public class OfferRelocation {

    /** Offers looked up per transaction: a lookup may wait on the geocoding service. */
    static final int BATCH = 50;

    private final JobOfferRepository offers;
    private final OfferNormalizer normalizer;

    /** Looks up to {@value #BATCH} offers after {@code afterId} up again; the last one looked at, or {@code null} when none was left. */
    @Transactional
    public Long locateStoredOffers(Long afterId) {
        List<JobOffer> unlocated = offers.findUnlocatedAfter(afterId, PageRequest.of(0, BATCH));
        unlocated.forEach(normalizer::relocate);
        return unlocated.isEmpty() ? null : unlocated.getLast().getId();
    }
}
