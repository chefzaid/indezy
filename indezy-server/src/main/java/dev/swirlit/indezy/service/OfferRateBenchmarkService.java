package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferRateMarketDto;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferRateBenchmark;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferRateBenchmarkRepository;
import dev.swirlit.indezy.util.FrenchGeography;
import dev.swirlit.indezy.util.TextFolding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The market an offer's daily rate is compared with (see docs/features.md#rate-versus-market): every night, the
 * median of the open offers stating a rate, one per mission, by the skill they name first, in each region and in all
 * of France, kept where ten offers or more make it; each offer is then measured against its skill in its region, else
 * in all of France.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfferRateBenchmarkService {

    /** Comparable offers a median needs to be shown. */
    static final int MIN_OFFERS = 10;
    /** How long the medians read from the database are kept in memory. */
    static final Duration KEEP_FOR = Duration.ofMinutes(10);

    /** A skill in a region (or in all of France). */
    record Key(String skillKey, String regionCode) {
    }

    /** The medians in memory, and when they were read. */
    private record Loaded(Map<Key, OfferRateBenchmark> byKey, Instant at) {
    }

    private final JobOfferRepository offers;
    private final OfferRateBenchmarkRepository benchmarks;
    private final Clock clock;
    private volatile Loaded loaded;

    /** Computes the medians again from the open offers and replaces the stored ones; how many were kept. */
    @Transactional
    public int refresh() {
        Instant now = clock.instant();
        Map<Object, Object[]> missions = new HashMap<>();
        for (Object[] row : offers.findRatedMainSkills(now)) {
            // One per mission: its copies on other sites share a copy group.
            Object mission = row[1] != null ? row[1] : "offer-" + row[0];
            missions.putIfAbsent(mission, row);
        }
        Map<Key, List<Integer>> rates = new HashMap<>();
        Map<String, Map<String, Integer>> spellings = new HashMap<>();
        for (Object[] row : missions.values()) {
            String skill = ((String) row[5]).strip();
            String skillKey = TextFolding.words(skill);
            Integer rate = middle((Integer) row[3], (Integer) row[4]);
            if (skillKey == null || skillKey.isBlank() || rate == null) {
                continue;
            }
            spellings.computeIfAbsent(skillKey, key -> new HashMap<>()).merge(skill, 1, Integer::sum);
            rates.computeIfAbsent(new Key(skillKey, OfferRateBenchmark.FRANCE), key -> new ArrayList<>()).add(rate);
            if (row[2] != null) {
                rates.computeIfAbsent(new Key(skillKey, (String) row[2]), key -> new ArrayList<>()).add(rate);
            }
        }
        List<OfferRateBenchmark> computed = new ArrayList<>();
        rates.forEach((key, values) -> {
            if (values.size() >= MIN_OFFERS) {
                computed.add(benchmark(key, mostWritten(spellings.get(key.skillKey())), values, now));
            }
        });
        benchmarks.deleteAllInBatch();
        benchmarks.saveAll(computed);
        loaded = null;
        log.info("Computed {} daily rate medians from {} missions", computed.size(), missions.size());
        return computed.size();
    }

    /** Whether no median was computed yet. */
    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return benchmarks.count() == 0;
    }

    /** The offer's rate against the market, or {@code null} without a rate, a skill ({@code skills} listed first) or a median. */
    public OfferRateMarketDto compare(JobOffer offer, List<String> skills) {
        return compare(offer.getRateMin(), offer.getRateMax(), offer.getRegionCode(), skills == null || skills.isEmpty() ? null : skills.get(0));
    }

    /** The rates of an offer of {@code regionCode} naming {@code mainSkill} first against the market, as {@link #compare(JobOffer, List)}. */
    public OfferRateMarketDto compare(Integer rateMin, Integer rateMax, String regionCode, String mainSkill) {
        Integer rate = middle(rateMin, rateMax);
        String skillKey = TextFolding.words(mainSkill);
        if (rate == null || skillKey == null || skillKey.isBlank()) {
            return null;
        }
        Map<Key, OfferRateBenchmark> byKey = medians();
        OfferRateBenchmark benchmark = regionCode == null ? null : byKey.get(new Key(skillKey, regionCode));
        if (benchmark == null) {
            benchmark = byKey.get(new Key(skillKey, OfferRateBenchmark.FRANCE));
        }
        if (benchmark == null) {
            return null;
        }
        int percent = (int) Math.round(100.0 * (rate - benchmark.getMedianRate()) / benchmark.getMedianRate());
        String region = FrenchGeography.region(benchmark.getRegionCode()).map(FrenchGeography.Region::name).orElse(null);
        return new OfferRateMarketDto(percent, benchmark.getMedianRate(), benchmark.getOffers(), benchmark.getSkill(), region);
    }

    /** The middle of the range an offer states, or the one rate it states. */
    static Integer middle(Integer rateMin, Integer rateMax) {
        if (rateMin != null && rateMax != null) {
            return Math.round((rateMin + rateMax) / 2f);
        }
        Integer rate = rateMax != null ? rateMax : rateMin;
        return rate != null && rate > 0 ? rate : null;
    }

    /** The middle value, or the mean of the two middle ones, rounded. */
    static int median(List<Integer> values) {
        List<Integer> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : Math.round((sorted.get(middle - 1) + sorted.get(middle)) / 2f);
    }

    private Map<Key, OfferRateBenchmark> medians() {
        Loaded current = loaded;
        Instant now = clock.instant();
        if (current == null || !current.at().plus(KEEP_FOR).isAfter(now)) {
            current = new Loaded(benchmarks.findAll().stream()
                .collect(Collectors.toMap(benchmark -> new Key(benchmark.getSkillKey(), benchmark.getRegionCode()), Function.identity())), now);
            loaded = current;
        }
        return current.byKey();
    }

    private static OfferRateBenchmark benchmark(Key key, String skill, List<Integer> rates, Instant now) {
        OfferRateBenchmark benchmark = new OfferRateBenchmark();
        benchmark.setSkillKey(key.skillKey());
        benchmark.setSkill(skill);
        benchmark.setRegionCode(key.regionCode());
        benchmark.setMedianRate(median(rates));
        benchmark.setOffers(rates.size());
        benchmark.setComputedAt(now);
        return benchmark;
    }

    /** The spelling written most, the first in alphabetical order among equals. */
    private static String mostWritten(Map<String, Integer> spellings) {
        Set<String> tied = new HashSet<>();
        int most = spellings.values().stream().max(Integer::compare).orElse(0);
        spellings.forEach((spelling, count) -> {
            if (count == most) {
                tied.add(spelling);
            }
        });
        return tied.stream().min(Comparator.naturalOrder()).orElseThrow();
    }
}
