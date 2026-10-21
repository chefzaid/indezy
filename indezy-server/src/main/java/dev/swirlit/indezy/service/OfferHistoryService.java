package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferHistoryDto;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferChange;
import dev.swirlit.indezy.repository.OfferAppearanceRepository;
import dev.swirlit.indezy.repository.OfferChangeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The history of the offers a page shows (see {@link OfferHistoryDto}). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferHistoryService {

    /** The span a mission's references are counted over. */
    static final Duration REPUBLISHED_WITHIN = Duration.ofDays(60);
    /** Changes kept per offer, the latest. */
    static final int MAX_CHANGES = 10;

    private final OfferChangeRepository changes;
    private final OfferAppearanceRepository appearances;

    /** Each offer's history, for the offers that have one. */
    public Map<Long, OfferHistoryDto> of(List<JobOffer> offers, Instant now) {
        if (offers.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<OfferHistoryDto.Change>> changesByOffer = new HashMap<>();
        for (OfferChange change : changes.findByOfferIds(offers.stream().map(JobOffer::getId).toList())) {
            List<OfferHistoryDto.Change> list = changesByOffer.computeIfAbsent(change.getOffer().getId(), id -> new ArrayList<>());
            if (list.size() < MAX_CHANGES) {
                list.add(new OfferHistoryDto.Change(change.getField(), change.getBefore(), change.getAfter(), change.getChangedAt()));
            }
        }
        List<String> keys = offers.stream().map(JobOffer::getPublisherKey).filter(Objects::nonNull).distinct().toList();
        Map<String, Integer> references = new HashMap<>();
        if (!keys.isEmpty()) {
            for (Object[] row : appearances.countSince(keys, now.minus(REPUBLISHED_WITHIN))) {
                references.put(row[0] + "|" + row[1], ((Number) row[2]).intValue());
            }
        }
        Map<Long, OfferHistoryDto> histories = new HashMap<>();
        for (JobOffer offer : offers) {
            int times = references.getOrDefault(offer.getSite() + "|" + offer.getPublisherKey(), 0);
            List<OfferHistoryDto.Change> offerChanges = changesByOffer.get(offer.getId());
            if (times > 1 || offerChanges != null) {
                histories.put(offer.getId(), new OfferHistoryDto(times > 1 ? times : null, offerChanges));
            }
        }
        return histories;
    }
}
