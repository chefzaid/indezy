package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.LostReason;

import java.time.LocalDate;
import java.util.List;

/**
 * A freelancer's year: the missions worked in it (days from the activity reports where there are some, else estimated),
 * what they brought compared with the year before, the opportunities opened and how they ended, the skills most asked
 * for, and the people and companies met.
 */
public record YearlyReviewDto(
    int year,
    String name,
    List<Mission> missions,
    Totals totals,
    Totals previousYear,
    Pipeline pipeline,
    List<SkillCount> topSkills,
    Network network) {

    public YearlyReviewDto {
        missions = List.copyOf(missions);
        topSkills = List.copyOf(topSkills);
    }

    /** A won mission's share of the year; {@code reported} when its days come from activity reports. */
    public record Mission(Long projectId, String role, String clientName, Integer dailyRate, LocalDate start, LocalDate end,
                          double days, boolean reported, Long revenue) {

        // Spring MVC logs bodies through toString at DEBUG: the id only, never a client's name (see docs/security.md).
        @Override
        public String toString() {
            return "Mission[projectId=" + projectId + "]";
        }
    }

    /** Revenue (€), days worked, the average daily rate they make (null without days) and the number of missions. */
    public record Totals(long revenue, double days, Integer averageDailyRate, int missions) {
    }

    /** Opportunities added in the year, those of them won and lost, won out of decided (%), and why they were lost. */
    public record Pipeline(int opened, int won, int lost, Integer conversionRate, List<ReasonCount> lostReasons) {

        public Pipeline {
            lostReasons = List.copyOf(lostReasons);
        }
    }

    public record ReasonCount(LostReason reason, int count) {
    }

    public record SkillCount(String skill, long count) {
    }

    /** Final clients, ESNs and contacts added in the year, and the exchanges recorded in it. */
    public record Network(int clients, int esns, int contacts, int exchanges) {
    }
}
