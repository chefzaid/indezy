package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.MarketReportDto;
import dev.swirlit.indezy.model.enums.Seniority;
import dev.swirlit.indezy.repository.OfferMatch;
import dev.swirlit.indezy.util.ExpiringCache;
import dev.swirlit.indezy.util.FrenchGeography;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The quarterly public market report (see docs/features.md#market-report), from the daily rates the feed's offers state:
 * public postings, never a member's own figures (those stay the members' benchmark, ADR 0014). Each mission counts once,
 * however many sites posted it; a figure is given for {@value #THRESHOLD} offers or more, else not at all.
 */
@Service
@RequiredArgsConstructor
public class MarketReportService {

    static final int THRESHOLD = 10;
    static final int SKILLS_SHOWN = 30;
    static final int QUARTERS_LISTED = 8;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Pattern QUARTER = Pattern.compile("(\\d{4})-Q([1-4])");

    private final EntityManager entityManager;
    private final Clock clock;
    private final ExpiringCache<String, MarketReportDto> reports = new ExpiringCache<>(Duration.ofHours(1), 16);

    /** One offer's stated rate for a skill. */
    record Point(Object mission, String skill, String region, Seniority seniority, int rate) {
    }

    /** The report of a quarter ("2026-Q3"); the last complete quarter by default. */
    @Transactional(readOnly = true)
    public MarketReportDto report(String quarter) {
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        List<String> quarters = new ArrayList<>();
        LocalDate start = quarterStart(today).minusMonths(3);
        for (int index = 0; index < QUARTERS_LISTED; index++) {
            quarters.add(name(start.minusMonths(3L * index)));
        }
        String wanted = quarter == null || quarter.isBlank() ? quarters.getFirst() : quarter.strip().toUpperCase(Locale.ROOT);
        if (!quarters.contains(wanted) && !wanted.equals(name(quarterStart(today)))) {
            throw new IllegalArgumentException("Pick one of the last " + QUARTERS_LISTED + " quarters, as 2026-Q3");
        }
        return reports.get(wanted, clock.instant(), key -> build(key, quarters));
    }

    private MarketReportDto build(String quarter, List<String> quarters) {
        LocalDate from = start(quarter);
        LocalDate to = from.plusMonths(3);
        List<Point> points = points(from, to);
        Map<String, Integer> previous = medians(points(from.minusMonths(3), from));
        Map<String, String> names = new HashMap<>();
        Map<String, List<Point>> bySkill = new HashMap<>();
        for (Point point : points) {
            String key = point.skill().toLowerCase(Locale.ROOT);
            names.putIfAbsent(key, point.skill());
            bySkill.computeIfAbsent(key, ignored -> new ArrayList<>()).add(point);
        }
        List<MarketReportDto.Skill> skills = bySkill.entrySet().stream().filter(entry -> missions(entry.getValue()) >= THRESHOLD)
            .sorted(Comparator.comparingInt((Map.Entry<String, List<Point>> entry) -> missions(entry.getValue())).reversed()
                .thenComparing(Map.Entry::getKey))
            .limit(SKILLS_SHOWN)
            .map(entry -> new MarketReportDto.Skill(names.get(entry.getKey()), figure(entry.getValue()), previous.get(entry.getKey()),
                grouped(entry.getValue(), Point::seniority, () -> new EnumMap<>(Seniority.class)),
                grouped(entry.getValue(), Point::region, HashMap::new)))
            .toList();
        // One rate per mission across its skills, for the overall and regional figures.
        Map<Object, Point> byMission = new LinkedHashMap<>();
        points.forEach(point -> byMission.putIfAbsent(point.mission(), point));
        List<Point> missions = new ArrayList<>(byMission.values());
        List<MarketReportDto.RegionFigure> regions = missions.stream().filter(point -> point.region() != null)
            .collect(Collectors.groupingBy(Point::region)).entrySet().stream().filter(entry -> entry.getValue().size() >= THRESHOLD)
            .map(entry -> new MarketReportDto.RegionFigure(entry.getKey(),
                FrenchGeography.region(entry.getKey()).map(FrenchGeography.Region::name).orElse(entry.getKey()), figure(entry.getValue())))
            .sorted(Comparator.comparingInt((MarketReportDto.RegionFigure region) -> region.figure().offers()).reversed())
            .toList();
        return new MarketReportDto(quarter, from, to.minusDays(1), missions.size(), THRESHOLD,
            missions.size() >= THRESHOLD ? figure(missions) : null, skills, regions, quarters);
    }

    /** The offers published in the period that state a rate, one point per skill they name. */
    private List<Point> points(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(PARIS).toInstant();
        Instant end = to.atStartOfDay(PARIS).toInstant();
        List<Object[]> rows = entityManager.createQuery("select o.id, o.copyGroup, s, o.regionCode, o.seniority, o.rateMin, o.rateMax " +
                "from JobOffer o join o.skills s where o.publishedAt >= :start and o.publishedAt < :end " +
                "and (o.rateMin is not null or o.rateMax is not null)", Object[].class)
            .setParameter("start", start).setParameter("end", end).getResultList();
        List<Point> points = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object[] row : rows) {
            Integer rate = OfferMatch.bestRate((Integer) row[5], (Integer) row[6]);
            String skill = row[2] == null ? null : ((String) row[2]).strip();
            if (rate == null || rate <= 0 || skill == null || skill.isEmpty()) {
                continue;
            }
            Object mission = row[1] != null ? "g" + row[1] : "o" + row[0];
            // A mission posted on several sites counts once per skill.
            if (seen.add(mission + "|" + skill.toLowerCase(Locale.ROOT))) {
                points.add(new Point(mission, skill, (String) row[3], (Seniority) row[4], rate));
            }
        }
        return points;
    }

    private static Map<String, Integer> medians(List<Point> points) {
        return points.stream().collect(Collectors.groupingBy(point -> point.skill().toLowerCase(Locale.ROOT))).entrySet().stream()
            .filter(entry -> missions(entry.getValue()) >= THRESHOLD)
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> figure(entry.getValue()).median()));
    }

    private static <K> Map<K, Integer> grouped(List<Point> points, Function<Point, K> key, Supplier<Map<K, Integer>> map) {
        Map<K, List<Point>> groups = new HashMap<>();
        points.stream().filter(point -> key.apply(point) != null).forEach(point -> groups.computeIfAbsent(key.apply(point), k -> new ArrayList<>()).add(point));
        Map<K, Integer> medians = map.get();
        groups.forEach((group, members) -> {
            if (missions(members) >= THRESHOLD) {
                medians.put(group, figure(members).median());
            }
        });
        return medians;
    }

    private static int missions(List<Point> points) {
        return (int) points.stream().map(Point::mission).distinct().count();
    }

    static MarketReportDto.Figure figure(List<Point> points) {
        List<Integer> rates = points.stream().map(Point::rate).sorted().toList();
        return new MarketReportDto.Figure(missions(points), RateBenchmarkService.percentile(rates, 0.5), RateBenchmarkService.percentile(rates, 0.25),
            RateBenchmarkService.percentile(rates, 0.75));
    }

    static LocalDate quarterStart(LocalDate day) {
        return LocalDate.of(day.getYear(), (day.getMonthValue() - 1) / 3 * 3 + 1, 1);
    }

    static String name(LocalDate start) {
        return start.getYear() + "-Q" + ((start.getMonthValue() - 1) / 3 + 1);
    }

    static LocalDate start(String quarter) {
        Matcher matcher = QUARTER.matcher(quarter);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("A quarter as 2026-Q3");
        }
        return LocalDate.of(Integer.parseInt(matcher.group(1)), (Integer.parseInt(matcher.group(2)) - 1) * 3 + 1, 1);
    }
}
