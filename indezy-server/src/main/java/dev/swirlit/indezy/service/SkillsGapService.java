package dev.swirlit.indezy.service;

import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.util.SkillNames;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The skills the feed asks for that the profile lacks (see docs/features.md#skills-gap): among the open offers naming at
 * least one of the profile's skills, the share naming each other skill, spellings and aliases of one skill counted once.
 * Shown from {@value #MATCHING_MIN} matching offers, skills in at least {@value #PERCENT_MIN} % of them and
 * {@value #OFFERS_MIN} offers, the {@value #GAPS_MAX} most asked first.
 */
@Service
@RequiredArgsConstructor
public class SkillsGapService {

    static final int MATCHING_MIN = 10;
    static final int PERCENT_MIN = 10;
    static final int OFFERS_MIN = 3;
    static final int GAPS_MAX = 6;
    /** The open offers' skills are read again at most this often: the feed changes overnight. */
    private static final Duration CACHE = Duration.ofMinutes(10);
    private static final int PERCENT = 100;

    private final JobOfferRepository offers;
    private final FreelanceRepository freelances;
    private final Clock clock;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    /** A skill the profile lacks: its usual name, the matching offers naming it, and their share. */
    public record Gap(String skill, int offers, int percent) {
    }

    /** The matching offers counted, and the gaps; none below {@value #MATCHING_MIN} matching offers. */
    public record SkillsGap(int matchingOffers, List<Gap> gaps) {

        public SkillsGap {
            gaps = List.copyOf(gaps);
        }
    }

    /** Each open offer's skill keys, and each key's name as most often written. */
    record Snapshot(Instant readAt, Map<Long, Set<String>> keysByOffer, Map<String, String> names) {
    }

    @Transactional(readOnly = true)
    public SkillsGap gap(Long freelanceId) {
        Set<String> own = new LinkedHashSet<>();
        freelances.findProfileSkills(freelanceId).forEach(skill -> own.add(SkillNames.key(SkillNames.canonical(skill))));
        own.remove("");
        if (own.isEmpty()) {
            return new SkillsGap(0, List.of());
        }
        Snapshot open = snapshot();
        return measure(own, open.keysByOffer(), open.names());
    }

    /** The gaps of a profile of skill keys {@code own} against offers' skill keys. */
    static SkillsGap measure(Set<String> own, Map<Long, Set<String>> keysByOffer, Map<String, String> names) {
        int matching = 0;
        Map<String, Integer> asked = new HashMap<>();
        for (Set<String> keys : keysByOffer.values()) {
            if (keys.stream().noneMatch(own::contains)) {
                continue;
            }
            matching++;
            keys.stream().filter(key -> !own.contains(key)).forEach(key -> asked.merge(key, 1, Integer::sum));
        }
        if (matching < MATCHING_MIN) {
            return new SkillsGap(matching, List.of());
        }
        int total = matching;
        List<Gap> gaps = asked.entrySet().stream()
            .map(entry -> new Gap(names.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue(),
                Math.round(entry.getValue() * (float) PERCENT / total)))
            .filter(gap -> gap.offers() >= OFFERS_MIN && gap.percent() >= PERCENT_MIN)
            .sorted(Comparator.comparingInt(Gap::offers).reversed().thenComparing(Gap::skill))
            .limit(GAPS_MAX)
            .toList();
        return new SkillsGap(matching, gaps);
    }

    private Snapshot snapshot() {
        Instant now = clock.instant();
        Snapshot current = snapshot.get();
        if (current != null && current.readAt().plus(CACHE).isAfter(now)) {
            return current;
        }
        Map<Long, Set<String>> keysByOffer = new HashMap<>();
        Map<String, Map<String, Integer>> spellings = new HashMap<>();
        for (Object[] row : offers.findOpenSkills(now)) {
            String name = SkillNames.canonical((String) row[1]);
            String key = SkillNames.key(name);
            if (key.isEmpty()) {
                continue;
            }
            keysByOffer.computeIfAbsent((Long) row[0], id -> new LinkedHashSet<>()).add(key);
            spellings.computeIfAbsent(key, k -> new HashMap<>()).merge(name, 1, Integer::sum);
        }
        Map<String, String> names = new LinkedHashMap<>();
        spellings.forEach((key, counts) -> names.put(key, new ArrayList<>(counts.entrySet()).stream()
            .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
            .map(Map.Entry::getKey).orElse(key)));
        Snapshot read = new Snapshot(now, keysByOffer, names);
        snapshot.set(read);
        return read;
    }
}
