package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

/**
 * How the market of a skill moved (see docs/features.md#market-trends): its median daily rate and open offers on the
 * latest night, how they changed over 30, 90 and 365 days, and every night of the last year. {@code skills} are the
 * profile's skills to choose from; {@code skill}, {@code latest} and {@code nights} are absent without one or without
 * history, and {@code regionName} in all of France.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferTrendDto(List<String> skills, String skill, String regionName, Night latest, List<Change> changes,
                            List<Night> nights) {

    public OfferTrendDto {
        skills = List.copyOf(skills);
        changes = changes == null ? null : List.copyOf(changes);
        nights = nights == null ? null : List.copyOf(nights);
    }

    /** One night's median and open offers. */
    public record Night(LocalDate day, int median, int offers) {
    }

    /**
     * The change over {@code days}: absent percentages when there was no night that far back (or not within a week of it).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Change(int days, Integer medianPercent, Integer offersPercent) {
    }
}
