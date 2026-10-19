package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferSkip;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferSkipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Saves the offers of one page in one transaction: new ones are added, known ones refreshed, and those the feed left
 * out are remembered as skipped.
 */
@Service
@RequiredArgsConstructor
public class OfferStore {

    /** The column's length; longer identifiers are not remembered. */
    private static final int MAX_EXTERNAL_ID_LENGTH = 191;

    private final JobOfferRepository offers;
    private final OfferSkipRepository skips;

    /** How many of {@code collected} (normalized offers of {@code site}) Indezy had never seen. */
    @Transactional
    public int save(OfferSite site, List<JobOffer> collected) {
        if (collected.isEmpty()) {
            return 0;
        }
        Map<String, JobOffer> byExternalId = new LinkedHashMap<>();
        collected.forEach(offer -> byExternalId.putIfAbsent(offer.getExternalId(), offer));
        Map<String, JobOffer> known = offers.findBySiteAndExternalIdIn(site, byExternalId.keySet()).stream()
            .collect(Collectors.toMap(JobOffer::getExternalId, Function.identity()));
        List<JobOffer> created = new ArrayList<>();
        for (JobOffer offer : byExternalId.values()) {
            JobOffer existing = known.get(offer.getExternalId());
            if (existing == null) {
                created.add(offer);
            } else {
                existing.updateFrom(offer);
            }
        }
        offers.saveAll(created);
        return created.size();
    }

    /** Remembers offers the feed left out, so readers of one page per offer do not fetch them again. */
    @Transactional
    public void skip(OfferSite site, Collection<String> externalIds, Instant now) {
        Set<String> fresh = new HashSet<>();
        for (String id : externalIds) {
            if (id != null && id.length() <= MAX_EXTERNAL_ID_LENGTH) {
                fresh.add(id);
            }
        }
        if (fresh.isEmpty()) {
            return;
        }
        fresh.removeAll(skips.findSkippedIds(site, fresh));
        skips.saveAll(fresh.stream().map(id -> new OfferSkip(site, id, now)).toList());
    }

    /** Which identifiers of a site are stored or recently skipped. */
    @Transactional(readOnly = true)
    public Set<String> known(OfferSite site, Collection<String> externalIds) {
        if (externalIds.isEmpty()) {
            return Set.of();
        }
        Set<String> known = new HashSet<>(offers.findKnownIds(site, externalIds));
        known.addAll(skips.findSkippedIds(site, externalIds));
        return known;
    }
}
