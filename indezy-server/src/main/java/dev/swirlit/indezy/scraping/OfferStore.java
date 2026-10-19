package dev.swirlit.indezy.scraping;

import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Saves the offers of one page in one transaction: new ones are added, known ones refreshed. */
@Service
@RequiredArgsConstructor
public class OfferStore {

    private final JobOfferRepository offers;

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
}
