package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.util.FrenchGeography;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads what the freelancer looks for, said in their own words, into the offer feed's filters (see
 * docs/features.md#ai-features): skills, a rate floor, remote days, work modes, regions, a start date, a minimum
 * duration and keywords, plus what they want to avoid, which the feed cannot filter on yet and is only shown back.
 */
@Service
@RequiredArgsConstructor
public class OfferBriefAiService {

    public static final int TEXT_MAX = 2_000;
    private static final int SKILLS_MAX = 8;
    private static final int RATE_MAX = 3_000;
    private static final int REMOTE_DAYS_MAX = 5;
    private static final int DURATION_MAX = 60;
    private static final long MAX_TOKENS = 1_500;
    private static final String SYSTEM = """
        You turn what a French freelance IT consultant says they look for into search filters for mission offers. Fill
        only what they say; leave the rest empty or 0. Regions are French administrative regions by their usual name
        (Île-de-France, Auvergne-Rhône-Alpes...). Dates are yyyy-MM-dd.""";

    private final AiService ai;

    /** What the AI reads. */
    public record Reading(
        @JsonPropertyDescription("Skills or technologies asked for") List<String> skills,
        @JsonPropertyDescription("The lowest daily rate accepted in euros, or 0") int rateMin,
        @JsonPropertyDescription("The fewest remote days a week accepted, or 0") int minRemoteDays,
        @JsonPropertyDescription("REMOTE, HYBRID or ONSITE modes accepted, or empty for any") List<WorkMode> workModes,
        @JsonPropertyDescription("French regions by name, or empty for anywhere") List<String> regions,
        @JsonPropertyDescription("The latest start date accepted, yyyy-MM-dd, or empty") String startBefore,
        @JsonPropertyDescription("The shortest duration accepted in months, or 0") int durationMin,
        @JsonPropertyDescription("Other words the offers must contain, or empty") String keywords,
        @JsonPropertyDescription("Sectors, clients or things to avoid, each a few words") List<String> avoid) {

        public Reading {
            skills = List.copyOf(clean(skills));
            workModes = workModes == null ? List.of() : List.copyOf(workModes.stream().filter(Objects::nonNull).distinct().toList());
            regions = List.copyOf(clean(regions));
            avoid = List.copyOf(clean(avoid));
        }
    }

    /** The filters read: region codes for the regions named, null for what was not said. */
    public record Brief(List<String> skills, Integer rateMin, Integer minRemoteDays, List<WorkMode> workModes, List<String> regions,
                        LocalDate startBefore, Integer durationMin, String q, List<String> avoid) {

        public Brief {
            skills = List.copyOf(skills);
            workModes = List.copyOf(workModes);
            regions = List.copyOf(regions);
            avoid = List.copyOf(avoid);
        }
    }

    public Brief read(Long userId, String text) {
        Reading reading = ai.structured(userId, AiFeature.POSTING, SYSTEM, "<brief>\n" + text.strip() + "\n</brief>", Reading.class, MAX_TOKENS,
            OutputConfig.Effort.LOW);
        return brief(reading);
    }

    static Brief brief(Reading reading) {
        return new Brief(reading.skills().stream().limit(SKILLS_MAX).toList(),
            reading.rateMin() > 0 ? Math.min(reading.rateMin(), RATE_MAX) : null,
            reading.minRemoteDays() > 0 ? Math.min(reading.minRemoteDays(), REMOTE_DAYS_MAX) : null,
            reading.workModes(),
            reading.regions().stream().map(FrenchGeography::regionNamed).flatMap(Optional::stream).map(FrenchGeography.Region::code)
                .distinct().toList(),
            date(reading.startBefore()),
            reading.durationMin() > 0 ? Math.min(reading.durationMin(), DURATION_MAX) : null,
            reading.keywords() == null || reading.keywords().isBlank() ? null : reading.keywords().strip(),
            reading.avoid());
    }

    private static LocalDate date(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).map(String::strip).filter(value -> !value.isEmpty())
            .distinct().toList();
    }
}
