package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferTrendDto;
import dev.swirlit.indezy.model.OfferRateBenchmark;
import dev.swirlit.indezy.model.OfferRateHistory;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.OfferRateHistoryRepository;
import dev.swirlit.indezy.util.FrenchGeography;
import dev.swirlit.indezy.util.TextFolding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the market of a skill moved over 30, 90 and 365 days, in a region or in all of France, from the nightly history
 * of the medians (see docs/features.md#market-trends).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OfferTrendService {

    /** The spans the latest night is compared over. */
    static final List<Integer> SPANS = List.of(30, 90, 365);
    /** How much earlier than a span a night may be and still stand for it. */
    static final int TOLERANCE_DAYS = 7;
    /** Profile skills offered to choose from. */
    static final int MAX_SKILLS = 20;

    private final OfferRateHistoryRepository history;
    private final FreelanceRepository freelances;
    private final Clock clock;

    /** The trends of {@code skill} (the profile's first one when unset) in region {@code regionCode} (all of France when unset). */
    public OfferTrendDto trends(Long freelanceId, String skill, String regionCode) {
        List<String> skills = profileSkills(freelanceId);
        String chosen = skill != null && !skill.isBlank() ? skill.strip() : skills.isEmpty() ? null : skills.get(0);
        String region = regionCode != null && FrenchGeography.region(regionCode).isPresent() ? regionCode : OfferRateBenchmark.FRANCE;
        String regionName = FrenchGeography.region(region).map(FrenchGeography.Region::name).orElse(null);
        String skillKey = TextFolding.words(chosen);
        if (skillKey == null || skillKey.isBlank()) {
            return new OfferTrendDto(skills, null, regionName, null, null, null);
        }
        LocalDate today = LocalDate.now(clock.withZone(OfferRateBenchmarkService.PARIS));
        LocalDate since = today.minusDays(SPANS.get(SPANS.size() - 1) + (long) TOLERANCE_DAYS);
        List<OfferRateHistory> nights = history.findBySkillKeyAndRegionCodeAndDayGreaterThanEqualOrderByDay(skillKey, region, since);
        if (nights.isEmpty()) {
            return new OfferTrendDto(skills, chosen, regionName, null, null, null);
        }
        OfferRateHistory latest = nights.get(nights.size() - 1);
        List<OfferTrendDto.Change> changes = new ArrayList<>();
        for (int days : SPANS) {
            OfferRateHistory then = nightAbout(nights, latest.getDay().minusDays(days));
            if (then == null) {
                changes.add(new OfferTrendDto.Change(days, null, null));
            } else {
                changes.add(new OfferTrendDto.Change(days, percent(then.getMedianRate(), latest.getMedianRate()),
                    percent(then.getOffers(), latest.getOffers())));
            }
        }
        return new OfferTrendDto(skills, latest.getSkill(), regionName, night(latest), changes, nights.stream().map(OfferTrendService::night).toList());
    }

    /** The latest night on or before {@code day}, if it is at most {@value #TOLERANCE_DAYS} days earlier. */
    private static OfferRateHistory nightAbout(List<OfferRateHistory> nights, LocalDate day) {
        OfferRateHistory found = null;
        for (OfferRateHistory night : nights) {
            if (!night.getDay().isAfter(day)) {
                found = night;
            }
        }
        return found != null && !found.getDay().isBefore(day.minusDays(TOLERANCE_DAYS)) ? found : null;
    }

    /** The profile's skills, distinct once folded, as the profile writes them. */
    private List<String> profileSkills(Long freelanceId) {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String skill : freelances.findProfileSkills(freelanceId)) {
            String key = TextFolding.words(skill);
            if (key != null && !key.isBlank() && byKey.size() < MAX_SKILLS) {
                byKey.putIfAbsent(key, skill.strip());
            }
        }
        return List.copyOf(byKey.values());
    }

    private static Integer percent(int then, int now) {
        return then == 0 ? null : (int) Math.round(100.0 * (now - then) / then);
    }

    private static OfferTrendDto.Night night(OfferRateHistory night) {
        return new OfferTrendDto.Night(night.getDay(), night.getMedianRate(), night.getOffers());
    }
}
