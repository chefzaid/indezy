package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferRateMarketDto;
import dev.swirlit.indezy.model.enums.OfferSuspicion;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferAppearanceRepository;
import dev.swirlit.indezy.repository.OfferChangeRepository;
import dev.swirlit.indezy.scraping.FakePublishers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
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
    /** References in {@link #REPOSTED_WITHIN} that make an unchanged mission look kept up to gather profiles. */
    static final int REPOSTED_TIMES = 6;
    static final Duration REPOSTED_WITHIN = Duration.ofDays(60);
    /** Offers updated per statement. */
    private static final int UPDATE_CHUNK = 500;

    private final JobOfferRepository offers;
    private final OfferRateBenchmarkService benchmarks;
    private final OfferAppearanceRepository appearances;
    private final OfferChangeRepository offerChanges;
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
        Set<Long> reposted = repostedUnchanged(now);
        Map<Integer, List<Long>> changed = new HashMap<>();
        for (Object[] row : rows) {
            Long id = (Long) row[0];
            EnumSet<OfferSuspicion> found = reasons(row, mainSkills.get(id), publishersByOpening);
            if (reposted.contains(id)) {
                found.add(OfferSuspicion.REPOSTED_UNCHANGED);
            }
            Integer suspicion = OfferSuspicion.mask(found);
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

    /** The open offers whose mission was published under {@value #REPOSTED_TIMES} references or more lately, never changed. */
    private Set<Long> repostedUnchanged(Instant now) {
        Set<String> missions = new HashSet<>();
        for (Object[] row : appearances.findRepostedSince(now.minus(REPOSTED_WITHIN), REPOSTED_TIMES)) {
            missions.add(row[0] + "|" + row[1]);
        }
        if (missions.isEmpty()) {
            return Set.of();
        }
        Set<Long> changedOffers = new HashSet<>(offerChanges.findChangedOfferIds());
        Set<Long> reposted = new HashSet<>();
        for (Object[] row : offers.findOpenPublisherKeys(now)) {
            Long id = (Long) row[0];
            if (missions.contains(row[1] + "|" + row[2]) && !changedOffers.contains(id)) {
                reposted.add(id);
            }
        }
        return reposted;
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
