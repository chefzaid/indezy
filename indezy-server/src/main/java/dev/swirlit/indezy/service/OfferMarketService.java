package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferFacetDto;
import dev.swirlit.indezy.dto.OfferMarketDto;
import dev.swirlit.indezy.dto.OfferMarketDto.DayCount;
import dev.swirlit.indezy.dto.OfferMarketDto.RateBin;
import dev.swirlit.indezy.dto.OfferMarketDto.Rates;
import dev.swirlit.indezy.dto.OfferMarketDto.Remote;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.JobOfferSpecifications;
import dev.swirlit.indezy.repository.OfferMarketQueries;
import dev.swirlit.indezy.repository.OfferMarketQueries.Figures;
import dev.swirlit.indezy.repository.OfferMatch;
import dev.swirlit.indezy.util.ExpiringCache;
import dev.swirlit.indezy.util.TextFolding;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * The market snapshot of a feed search: how many missions it finds, what they pay, how remote they are, the skills
 * and intermediaries they name most and how many were published each day. An offer's daily rate is the middle of the
 * range it states (or its one rate); whether it pays the profile's target is read from its best rate, as the profile
 * match does.
 */
@Service
public class OfferMarketService {

    static final int TOP = 10;
    static final int DAYS = 30;
    static final int BIN_WIDTH = 50;
    static final int MAX_BINS = 16;
    /** The bands cover the rates between these percentiles; rarer ones fall in the open first and last bands. */
    static final double LOW_PERCENTILE = 0.05;
    static final double HIGH_PERCENTILE = 0.95;
    /** The job boards are French: days start in Paris. */
    static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    /** How long a snapshot is kept: an offer saved or hidden shows in the panel at most this late. */
    static final Duration KEEP_FOR = Duration.ofMinutes(1);
    static final int MAX_SNAPSHOTS = 500;

    /** A workspace's filters, with the sort, page and total left aside: they change the list, not the snapshot. */
    record Key(Long freelanceId, OfferSearchRequest filters) {
    }

    private final OfferMarketQueries queries;
    private final OfferSearchService search;
    private final Clock clock;
    private final ExpiringCache<Key, OfferMarketDto> snapshots = new ExpiringCache<>(KEEP_FOR, MAX_SNAPSHOTS);

    @Autowired
    public OfferMarketService(OfferMarketQueries queries, OfferSearchService search) {
        this(queries, search, Clock.systemUTC());
    }

    OfferMarketService(OfferMarketQueries queries, OfferSearchService search, Clock clock) {
        this.queries = queries;
        this.search = search;
        this.clock = clock;
    }

    /** The snapshot of the offers {@code request} finds for {@code freelanceId}'s workspace (or none); its sort and page are left aside. */
    public OfferMarketDto snapshot(Long freelanceId, OfferSearchRequest request) {
        Instant now = clock.instant();
        // About a second with 40,000 open offers: the panel reads each workspace's filters once a minute at most.
        return snapshots.get(new Key(freelanceId, filtersOf(request)), now, key -> compute(freelanceId, request, now));
    }

    static OfferSearchRequest filtersOf(OfferSearchRequest request) {
        // The request is read once and dropped with its HTTP request, so sharing its lists is safe.
        OfferSearchRequest filters = new OfferSearchRequest();
        BeanUtils.copyProperties(request, filters);
        filters.setSort(OfferSearchRequest.Sort.NEWEST);
        filters.setPage(0);
        filters.setSize(20);
        filters.setTotal(null);
        return filters;
    }

    private OfferMarketDto compute(Long freelanceId, OfferSearchRequest request, Instant now) {
        OfferMatch.Profile profile = search.profileFor(freelanceId, request);
        Specification<JobOffer> found = JobOfferSpecifications.matching(request, freelanceId, profile, now);
        List<Figures> figures = queries.figures(found);
        List<Integer> rates = figures.stream().map(OfferMarketService::rate).filter(Objects::nonNull).sorted().toList();
        return new OfferMarketDto(figures.size(), rates(figures, rates, profile.targetRate()), rateBins(rates), remote(figures),
            queries.topSkills(found, TOP), intermediaries(figures), perDay(figures, now));
    }

    /** The middle of the offer's range, or the one rate it states. */
    static Integer rate(Figures offer) {
        if (offer.rateMin() != null && offer.rateMax() != null) {
            return Math.round((offer.rateMin() + offer.rateMax()) / 2.0f);
        }
        return offer.rateMin() != null ? offer.rateMin() : offer.rateMax();
    }

    static Rates rates(List<Figures> figures, List<Integer> sorted, Integer target) {
        if (sorted.isEmpty()) {
            return new Rates(0, null, null, null, target, null);
        }
        Integer meetTarget = null;
        if (target != null) {
            long meeting = figures.stream()
                .map(offer -> OfferMatch.bestRate(offer.rateMin(), offer.rateMax()))
                .filter(best -> best != null && best >= target)
                .count();
            meetTarget = (int) Math.round(100.0 * meeting / sorted.size());
        }
        return new Rates(sorted.size(), percentile(sorted, 0.25), percentile(sorted, 0.5), percentile(sorted, 0.75), target, meetTarget);
    }

    /** The rate below which {@code share} of the sorted rates fall, interpolated between the two nearest ones. */
    static int percentile(List<Integer> sorted, double share) {
        double position = share * (sorted.size() - 1);
        int below = (int) Math.floor(position);
        int above = Math.min(below + 1, sorted.size() - 1);
        return (int) Math.round(sorted.get(below) + (position - below) * (sorted.get(above) - sorted.get(below)));
    }

    /**
     * Bands of {@value #BIN_WIDTH} euros (twice as wide, as often as needed, to keep {@value #MAX_BINS} at most) over the
     * usual rates, plus an open band below and one above for the rare ones, when there are.
     */
    static List<RateBin> rateBins(List<Integer> sorted) {
        if (sorted.isEmpty()) {
            return List.of();
        }
        int usualLow = percentile(sorted, LOW_PERCENTILE);
        int usualHigh = percentile(sorted, HIGH_PERCENTILE);
        int width = BIN_WIDTH;
        while (Math.floorDiv(usualHigh, width) - Math.floorDiv(usualLow, width) + 1 > MAX_BINS) {
            width *= 2;
        }
        int low = Math.floorDiv(usualLow, width) * width;
        int high = (Math.floorDiv(usualHigh, width) + 1) * width;
        long[] counts = new long[(high - low) / width];
        long under = 0;
        long over = 0;
        for (int rate : sorted) {
            if (rate < low) {
                under++;
            } else if (rate >= high) {
                over++;
            } else {
                counts[(rate - low) / width]++;
            }
        }
        List<RateBin> bins = new ArrayList<>();
        if (under > 0) {
            bins.add(new RateBin(null, low, under));
        }
        for (int band = 0; band < counts.length; band++) {
            bins.add(new RateBin(low + band * width, low + (band + 1) * width, counts[band]));
        }
        if (over > 0) {
            bins.add(new RateBin(high, null, over));
        }
        return bins;
    }

    static Remote remote(List<Figures> figures) {
        Map<WorkMode, Long> byMode = figures.stream()
            .filter(offer -> offer.workMode() != null)
            .collect(Collectors.groupingBy(Figures::workMode, Collectors.counting()));
        long stated = byMode.values().stream().mapToLong(Long::longValue).sum();
        OptionalDouble hybridDays = figures.stream()
            .filter(offer -> offer.workMode() == WorkMode.HYBRID && offer.remoteDaysPerWeek() != null)
            .mapToInt(Figures::remoteDaysPerWeek)
            .average();
        return new Remote(byMode.getOrDefault(WorkMode.REMOTE, 0L), byMode.getOrDefault(WorkMode.HYBRID, 0L),
            byMode.getOrDefault(WorkMode.ONSITE, 0L), figures.size() - stated,
            hybridDays.isPresent() ? Math.round(hybridDays.getAsDouble() * 10) / 10.0 : null);
    }

    /** The publishers naming the most offers, spellings that fold to the same words counting as one, under their most used one. */
    static List<OfferFacetDto> intermediaries(List<Figures> figures) {
        Map<String, Map<String, Long>> spellings = new HashMap<>();
        for (Figures offer : figures) {
            String folded = TextFolding.words(offer.company());
            if (folded != null && !folded.isBlank()) {
                spellings.computeIfAbsent(folded, key -> new HashMap<>()).merge(offer.company().strip(), 1L, Long::sum);
            }
        }
        return spellings.values().stream()
            .map(names -> new OfferFacetDto(
                names.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(),
                names.values().stream().mapToLong(Long::longValue).sum()))
            .sorted(Comparator.comparingLong(OfferFacetDto::offers).reversed().thenComparing(OfferFacetDto::value))
            .limit(TOP)
            .toList();
    }

    /** Offers published each of the last {@value #DAYS} days, today included. */
    static List<DayCount> perDay(List<Figures> figures, Instant now) {
        LocalDate today = now.atZone(ZONE).toLocalDate();
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (int daysAgo = DAYS - 1; daysAgo >= 0; daysAgo--) {
            counts.put(today.minusDays(daysAgo), 0L);
        }
        figures.stream()
            .map(Figures::publishedAt)
            .filter(Objects::nonNull)
            .map(published -> published.atZone(ZONE).toLocalDate())
            .filter(counts::containsKey)
            .forEach(day -> counts.merge(day, 1L, Long::sum));
        return counts.entrySet().stream().map(day -> new DayCount(day.getKey(), day.getValue())).toList();
    }
}
