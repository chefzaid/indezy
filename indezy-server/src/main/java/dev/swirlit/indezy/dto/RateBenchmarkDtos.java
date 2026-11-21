package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.RateRegion;
import dev.swirlit.indezy.model.enums.Seniority;

import java.util.List;

/** The network's rate benchmark: daily rates members share, anonymized, by skill, seniority and region. */
public final class RateBenchmarkDtos {

    private RateBenchmarkDtos() {
    }

    /**
     * A figure: how many freelancers back it and, once there are {@code threshold} of them or more, the median daily
     * rate and the middle half (25th to 75th percentile); null below. {@code seniority} and {@code region} are null for all.
     */
    public record Figure(String skill, Seniority seniority, RateRegion region, int freelancers, int threshold, Integer median, Integer low,
                         Integer high, boolean sharing) {
    }

    /** A skill enough freelancers back to give a figure, with how many. */
    public record Skill(String skill, int freelancers) {
    }

    public record Skills(List<Skill> skills, int threshold, boolean sharing) {

        public Skills {
            skills = List.copyOf(skills);
        }
    }
}
