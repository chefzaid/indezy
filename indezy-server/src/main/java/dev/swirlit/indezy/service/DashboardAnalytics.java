package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Pure computations for the dashboard's analytics: activity heatmap, process durations, skill
 * demand, conversion funnels, daily-rate evolution, bench time and source ROI.
 */
final class DashboardAnalytics {

    /** Days of prospection activity surfaced in the heatmap. */
    static final int HEATMAP_WINDOW_DAYS = 365;

    private static final int TOP_SKILLS_LIMIT = 12;

    private DashboardAnalytics() {
    }

    /**
     * Counts prospection activity per day over the last {@link #HEATMAP_WINDOW_DAYS} days: each
     * opportunity created and each interview step dated that day. Steps are expected to already be
     * restricted to the window. Only days with activity are returned, ordered chronologically.
     */
    static List<DashboardStatsDto.ActivityDay> buildActivityHeatmap(
            List<Project> projects, List<InterviewStep> steps, LocalDate today) {
        LocalDate from = today.minusDays(HEATMAP_WINDOW_DAYS);
        Map<LocalDate, Integer> counts = new HashMap<>();

        for (Project project : projects) {
            if (project.getCreatedAt() == null) {
                continue;
            }
            LocalDate day = project.getCreatedAt().toLocalDate();
            if (!day.isBefore(from) && !day.isAfter(today)) {
                counts.merge(day, 1, Integer::sum);
            }
        }
        for (InterviewStep step : steps) {
            if (step.getDate() != null) {
                counts.merge(step.getDate().toLocalDate(), 1, Integer::sum);
            }
        }

        return counts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> DashboardStatsDto.ActivityDay.builder()
                .date(entry.getKey())
                .count(entry.getValue())
                .build())
            .toList();
    }

    /** Maps each project id to the date of its latest validated interview step (its "signature"). */
    static Map<Long, LocalDateTime> signatureDates(List<InterviewStep> validatedSteps) {
        Map<Long, LocalDateTime> signatures = new HashMap<>();
        for (InterviewStep step : validatedSteps) {
            if (step.getDate() == null) {
                continue;
            }
            Long projectId = step.getProject().getId();
            signatures.merge(projectId, step.getDate(), (a, b) -> a.isAfter(b) ? a : b);
        }
        return signatures;
    }

    /**
     * Averages the number of days from first contact (opportunity creation) to signature (latest
     * validated step) for won opportunities, grouped by the ESN when present, otherwise the client.
     * Longest processes first; groups with no signed opportunity are omitted.
     */
    static List<DashboardStatsDto.ProcessDuration> buildProcessDurations(
            List<Project> projects, Map<Long, LocalDateTime> signatureDates) {
        Map<String, long[]> totalsByGroup = new LinkedHashMap<>();
        for (Project project : projects) {
            if (!ProjectStatus.WON.equals(project.getStatus()) || project.getCreatedAt() == null) {
                continue;
            }
            LocalDateTime signature = signatureDates.get(project.getId());
            if (signature == null) {
                continue;
            }
            long days = ChronoUnit.DAYS.between(project.getCreatedAt().toLocalDate(), signature.toLocalDate());
            if (days < 0) {
                continue;
            }
            String group = project.getMiddleman() != null
                ? project.getMiddleman().getCompanyName()
                : (project.getClient() != null ? project.getClient().getCompanyName() : null);
            if (group == null) {
                continue;
            }
            long[] totals = totalsByGroup.computeIfAbsent(group, key -> new long[2]);
            totals[0] += days;
            totals[1]++;
        }
        return totalsByGroup.entrySet().stream()
            .map(entry -> DashboardStatsDto.ProcessDuration.builder()
                .group(entry.getKey())
                .averageDays((double) entry.getValue()[0] / entry.getValue()[1])
                .count(entry.getValue()[1])
                .build())
            .sorted(Comparator.comparingDouble(DashboardStatsDto.ProcessDuration::getAverageDays).reversed())
            .toList();
    }

    /**
     * Ranks the skills/technologies appearing across opportunities' tech stacks by how often they
     * occur, alongside the average daily rate of the opportunities requiring each. Tech stacks are
     * split on commas and grouped case-insensitively. Most in-demand first.
     */
    static List<DashboardStatsDto.SkillTrend> buildSkillTrends(List<Project> projects) {
        Map<String, SkillAccumulator> bySkill = new LinkedHashMap<>();
        for (Project project : projects) {
            String techStack = project.getTechStack();
            if (techStack == null || techStack.isBlank()) {
                continue;
            }
            for (String rawSkill : techStack.split(",")) {
                String skill = rawSkill.trim();
                if (skill.isEmpty()) {
                    continue;
                }
                SkillAccumulator acc = bySkill.computeIfAbsent(
                    skill.toLowerCase(Locale.ROOT), key -> new SkillAccumulator(skill));
                acc.count++;
                if (project.getDailyRate() != null) {
                    acc.rateSum += project.getDailyRate();
                    acc.rateCount++;
                }
            }
        }
        return bySkill.values().stream()
            .map(acc -> DashboardStatsDto.SkillTrend.builder()
                .skill(acc.displayName)
                .count(acc.count)
                .averageDailyRate(acc.rateCount > 0 ? acc.rateSum / acc.rateCount : 0)
                .build())
            .sorted(Comparator.comparingLong(DashboardStatsDto.SkillTrend::getCount).reversed()
                .thenComparing(Comparator.comparingDouble(DashboardStatsDto.SkillTrend::getAverageDailyRate).reversed()))
            .limit(TOP_SKILLS_LIMIT)
            .toList();
    }

    /** Mutable tally for a single skill while aggregating tech stacks. */
    private static final class SkillAccumulator {
        private final String displayName;
        private long count;
        private double rateSum;
        private long rateCount;

        private SkillAccumulator(String displayName) {
            this.displayName = displayName;
        }
    }

    /**
     * Splits opportunities into groups by the given classifier and builds a conversion funnel for
     * each, so drop-off can be compared across sources, client types or ESNs. Opportunities the
     * classifier maps to {@code null} are skipped; groups are ordered by name.
     */
    static List<DashboardStatsDto.FunnelBreakdown> buildFunnelBreakdown(
            List<Project> projects, Function<Project, String> classifier) {
        Map<String, List<Project>> grouped = new TreeMap<>();
        for (Project project : projects) {
            String group = classifier.apply(project);
            if (group == null) {
                continue;
            }
            grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(project);
        }

        List<DashboardStatsDto.FunnelBreakdown> breakdowns = new ArrayList<>();
        for (Map.Entry<String, List<Project>> entry : grouped.entrySet()) {
            breakdowns.add(DashboardStatsDto.FunnelBreakdown.builder()
                .group(entry.getKey())
                .stages(buildConversionFunnel(entry.getValue()))
                .build());
        }
        return breakdowns;
    }

    /**
     * Builds the pipeline conversion funnel from the current status of each opportunity. Lost
     * opportunities are excluded. Because statuses are ordered, an opportunity at a later stage has
     * passed every earlier one; the conversion rate is relative to the first stage.
     */
    static List<DashboardStatsDto.ConversionFunnelStage> buildConversionFunnel(List<Project> projects) {
        ProjectStatus[] stages = {
            ProjectStatus.IDENTIFIED, ProjectStatus.APPLIED, ProjectStatus.INTERVIEW,
            ProjectStatus.OFFER, ProjectStatus.WON
        };
        long[] reached = new long[stages.length];
        for (Project project : projects) {
            ProjectStatus status = project.getStatus();
            if (status == null || status == ProjectStatus.LOST) {
                continue;
            }
            for (int i = 0; i < stages.length; i++) {
                if (status.ordinal() >= stages[i].ordinal()) {
                    reached[i]++;
                }
            }
        }

        long base = reached[0];
        List<DashboardStatsDto.ConversionFunnelStage> funnel = new ArrayList<>();
        for (int i = 0; i < stages.length; i++) {
            double rate = base == 0 ? 0 : Math.round(reached[i] * 1000.0 / base) / 10.0;
            funnel.add(DashboardStatsDto.ConversionFunnelStage.builder()
                .stage(stages[i].name())
                .count(reached[i])
                .conversionRate(rate)
                .build());
        }
        return funnel;
    }

    /**
     * Average asked vs obtained (agreed) daily rate per year of start date, ordered chronologically,
     * so a freelance can see how negotiated rates trend over time.
     */
    static List<DashboardStatsDto.DailyRateEvolution> buildDailyRateEvolution(List<Project> projects) {
        // Per year: [askedSum, askedCount, obtainedSum, obtainedCount, projectCount].
        Map<Integer, long[]> byYear = new TreeMap<>();
        for (Project project : projects) {
            if (project.getStartDate() == null) {
                continue;
            }
            long[] acc = byYear.computeIfAbsent(project.getStartDate().getYear(), k -> new long[5]);
            if (project.getAskedDailyRate() != null) {
                acc[0] += project.getAskedDailyRate();
                acc[1]++;
            }
            if (project.getDailyRate() != null) {
                acc[2] += project.getDailyRate();
                acc[3]++;
            }
            acc[4]++;
        }

        List<DashboardStatsDto.DailyRateEvolution> evolution = new ArrayList<>();
        for (Map.Entry<Integer, long[]> entry : byYear.entrySet()) {
            long[] acc = entry.getValue();
            evolution.add(DashboardStatsDto.DailyRateEvolution.builder()
                .period(String.valueOf(entry.getKey()))
                .averageAskedRate(acc[1] == 0 ? 0 : Math.round((double) acc[0] / acc[1]))
                .averageObtainedRate(acc[3] == 0 ? 0 : Math.round((double) acc[2] / acc[3]))
                .projectCount(acc[4])
                .build());
        }
        return evolution;
    }

    /**
     * Computes idle ("bench") time between consecutive signed missions. A gap is counted only when a
     * mission starts after the latest end seen so far, so overlapping missions never produce
     * negative bench. Returns {@code [totalBenchDays, benchPeriods]}.
     */
    static long[] buildBenchStats(List<Project> projects) {
        List<Project> missions = projects.stream()
            .filter(p -> ProjectStatus.WON.equals(p.getStatus())
                && p.getStartDate() != null && p.getDurationInMonths() != null)
            .sorted(Comparator.comparing(Project::getStartDate))
            .toList();

        long totalBenchDays = 0;
        long benchPeriods = 0;
        LocalDate previousEnd = null;
        for (Project mission : missions) {
            LocalDate start = mission.getStartDate();
            LocalDate end = start.plusMonths(mission.getDurationInMonths());
            if (previousEnd != null && start.isAfter(previousEnd)) {
                totalBenchDays += ChronoUnit.DAYS.between(previousEnd, start);
                benchPeriods++;
            }
            if (previousEnd == null || end.isAfter(previousEnd)) {
                previousEnd = end;
            }
        }
        return new long[]{totalBenchDays, benchPeriods};
    }

    /**
     * Ranks each opportunity source by how many of its opportunities turned into signed (WON)
     * contracts. Sorted by signed contracts, then conversion rate, then name.
     */
    static List<DashboardStatsDto.SourceRoi> buildSourceRoiRanking(List<Project> projects) {
        Map<String, long[]> totalsBySource = new LinkedHashMap<>();
        for (Project project : projects) {
            if (project.getSource() == null || project.getSource().getName() == null) {
                continue;
            }
            long[] counts = totalsBySource.computeIfAbsent(project.getSource().getName(), k -> new long[2]);
            counts[0]++;
            if (ProjectStatus.WON.equals(project.getStatus())) {
                counts[1]++;
            }
        }

        return totalsBySource.entrySet().stream()
            .map(entry -> {
                long total = entry.getValue()[0];
                long won = entry.getValue()[1];
                double rate = total == 0 ? 0 : Math.round(won * 1000.0 / total) / 10.0;
                return DashboardStatsDto.SourceRoi.builder()
                    .sourceName(entry.getKey())
                    .totalProjects(total)
                    .wonProjects(won)
                    .conversionRate(rate)
                    .build();
            })
            .sorted(Comparator.comparingLong(DashboardStatsDto.SourceRoi::getWonProjects).reversed()
                .thenComparing(Comparator.comparingDouble(DashboardStatsDto.SourceRoi::getConversionRate).reversed())
                .thenComparing(DashboardStatsDto.SourceRoi::getSourceName))
            .toList();
    }
}
