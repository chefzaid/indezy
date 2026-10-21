package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferRateMarketDto;
import dev.swirlit.indezy.model.enums.OfferSuspicion;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.scraping.FakePublishers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Flags the open offers that look like fake or spam listings (see docs/features.md#suspicious-offers), by the rules of
 * {@link OfferSuspicion}: their reasons are stored on each offer, so the feed can show them and leave them out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfferSuspicionService {

    /** Publishers posting the same opening text that make it look copied around. */
    static final int MANY_PUBLISHERS = 4;
    /** A rate below 40% of the median is 60% below it, and one above 250% of it 150% above. */
    static final int FAR_BELOW_PERCENT = -60;
    static final int FAR_ABOVE_PERCENT = 150;
    /** Offers updated per statement. */
    private static final int UPDATE_CHUNK = 500;

    private final JobOfferRepository offers;
    private final OfferRateBenchmarkService benchmarks;
    private final Clock clock;

    /** Reads the rules again for every open offer and stores the reasons that changed; how many offers changed. */
    @Transactional
    public int refresh() {
        Instant now = clock.instant();
        Map<Long, String> mainSkills = new HashMap<>();
        for (Object[] row : offers.findMainSkills(now)) {
            mainSkills.put((Long) row[0], (String) row[1]);
        }
        List<Object[]> rows = offers.findSuspicionInputs(now);
        Map<String, Set<String>> publishersByOpening = new HashMap<>();
        for (Object[] row : rows) {
            if (row[1] != null && row[2] != null) {
                publishersByOpening.computeIfAbsent((String) row[2], opening -> new HashSet<>()).add((String) row[1]);
            }
        }
        Map<Integer, List<Long>> changed = new HashMap<>();
        for (Object[] row : rows) {
            Long id = (Long) row[0];
            Integer suspicion = OfferSuspicion.mask(reasons(row, mainSkills.get(id), publishersByOpening));
            if (!Objects.equals(suspicion, row[6])) {
                changed.computeIfAbsent(suspicion, mask -> new ArrayList<>()).add(id);
            }
        }
        changed.forEach((suspicion, ids) -> {
            for (int start = 0; start < ids.size(); start += UPDATE_CHUNK) {
                offers.setSuspicion(suspicion, ids.subList(start, Math.min(ids.size(), start + UPDATE_CHUNK)));
            }
        });
        int count = changed.values().stream().mapToInt(List::size).sum();
        log.info("Read the suspicion rules of {} open offers: {} changed", rows.size(), count);
        return count;
    }

    private EnumSet<OfferSuspicion> reasons(Object[] row, String mainSkill, Map<String, Set<String>> publishersByOpening) {
        String publisher = (String) row[1];
        String opening = (String) row[2];
        EnumSet<OfferSuspicion> reasons = EnumSet.noneOf(OfferSuspicion.class);
        if (publisher == null && Boolean.TRUE.equals(row[7])) {
            reasons.add(OfferSuspicion.NO_PUBLISHER_NO_DESCRIPTION);
        }
        if (FakePublishers.named(publisher)) {
            reasons.add(OfferSuspicion.KNOWN_FAKE_PUBLISHER);
        }
        if (opening != null && publishersByOpening.getOrDefault(opening, Set.of()).size() >= MANY_PUBLISHERS) {
            reasons.add(OfferSuspicion.SAME_TEXT_MANY_PUBLISHERS);
        }
        OfferRateMarketDto market = benchmarks.compare((Integer) row[3], (Integer) row[4], (String) row[5], mainSkill);
        if (market != null && (market.percent() <= FAR_BELOW_PERCENT || market.percent() >= FAR_ABOVE_PERCENT)) {
            reasons.add(OfferSuspicion.RATE_OUTSIDE_MARKET);
        }
        return reasons;
    }
}
