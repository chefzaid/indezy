package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.Seniority;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The quarterly public market report (see docs/features.md#market-report): the daily rates the feed's offers published in
 * a quarter state, by skill, region and seniority. A figure is given only when {@code threshold} offers back it.
 */
public record MarketReportDto(String quarter, LocalDate from, LocalDate to, int offers, int threshold, Figure overall,
                              List<Skill> skills, List<RegionFigure> regions, List<String> quarters) {

    public MarketReportDto {
        skills = List.copyOf(skills);
        regions = List.copyOf(regions);
        quarters = List.copyOf(quarters);
    }

    /** How many offers back a figure, their median daily rate and middle half. */
    public record Figure(int offers, int median, int low, int high) {
    }

    /** A skill's figure, the previous quarter's median when it had one, and its figures by seniority and region. */
    public record Skill(String skill, Figure figure, Integer previousMedian, Map<Seniority, Integer> bySeniority,
                        Map<String, Integer> byRegion) {

        public Skill {
            bySeniority = Map.copyOf(bySeniority);
            byRegion = Map.copyOf(byRegion);
        }
    }

    /** A region's figure across skills. */
    public record RegionFigure(String regionCode, String name, Figure figure) {
    }
}
