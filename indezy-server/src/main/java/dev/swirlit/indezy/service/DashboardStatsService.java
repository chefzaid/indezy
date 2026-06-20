package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.TreeMap;

/**
 * Builds the aggregated dashboard statistics (counts, revenue, rate distribution, source ROI,
 * rate evolution, bench time) for a freelance. Kept separate from {@link ProjectService} so the
 * read-only analytics concern stays cohesive and independent of project CRUD.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardStatsService {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats(Long freelanceId) {
        log.debug("Getting dashboard stats for freelance: {}", freelanceId);

        Long totalProjects = projectRepository.countByFreelanceId(freelanceId);
        Double averageDailyRate = projectRepository.findAverageDailyRateByFreelanceId(freelanceId);
        Long wonProjects = projectRepository.countWonByFreelanceId(freelanceId);
        Long lostProjects = projectRepository.countLostByFreelanceId(freelanceId);
        Long activeProjects = projectRepository.countActiveByFreelanceId(freelanceId);

        // Projects by status
        Map<String, Long> projectsByStatus = new LinkedHashMap<>();
        for (ProjectStatus status : ProjectStatus.values()) {
            projectsByStatus.put(status.name(), 0L);
        }
        for (Object[] row : projectRepository.countByFreelanceIdGroupByStatus(freelanceId)) {
            ProjectStatus status = (ProjectStatus) row[0];
            Long count = (Long) row[1];
            projectsByStatus.put(status.name(), count);
        }

        // Projects by work mode
        Map<String, Long> projectsByWorkMode = new LinkedHashMap<>();
        for (WorkMode mode : WorkMode.values()) {
            projectsByWorkMode.put(mode.name(), 0L);
        }
        for (Object[] row : projectRepository.countByFreelanceIdGroupByWorkMode(freelanceId)) {
            WorkMode mode = (WorkMode) row[0];
            Long count = (Long) row[1];
            projectsByWorkMode.put(mode.name(), count);
        }

        // Daily rate ranges
        List<Project> projects = projectRepository.findByFreelanceId(freelanceId);

        // Lost-reason breakdown (only lost opportunities that carry a reason)
        Map<String, Long> lostReasonsBreakdown = new LinkedHashMap<>();
        for (LostReason reason : LostReason.values()) {
            lostReasonsBreakdown.put(reason.name(), 0L);
        }
        projects.stream()
            .filter(p -> ProjectStatus.LOST.equals(p.getStatus()) && p.getLostReason() != null)
            .forEach(p -> lostReasonsBreakdown.merge(p.getLostReason().name(), 1L, Long::sum));
        int[][] ranges = {{0, 300}, {300, 500}, {500, 700}, {700, 900}, {900, Integer.MAX_VALUE}};
        String[] rangeLabels = {"0-300", "300-500", "500-700", "700-900", "900+"};
        List<DashboardStatsDto.DailyRateRange> dailyRateRanges = new ArrayList<>();
        for (int i = 0; i < ranges.length; i++) {
            int min = ranges[i][0];
            int max = ranges[i][1];
            long count = projects.stream()
                .filter(p -> p.getDailyRate() != null && p.getDailyRate() >= min && p.getDailyRate() < max)
                .count();
            dailyRateRanges.add(DashboardStatsDto.DailyRateRange.builder()
                .label(rangeLabels[i])
                .count(count)
                .build());
        }

        // Total estimated revenue
        double totalRevenue = projects.stream()
            .filter(p -> p.getTotalRevenue() != null)
            .mapToDouble(Project::getTotalRevenue)
            .sum();

        // Forecast revenue: total revenue weighted by each opportunity's win probability
        double forecastRevenue = projects.stream()
            .filter(p -> p.getForecastRevenue() != null)
            .mapToDouble(Project::getForecastRevenue)
            .sum();

        List<DashboardStatsDto.SourceRoi> sourceRoi = buildSourceRoiRanking(projects);
        List<DashboardStatsDto.DailyRateEvolution> dailyRateEvolution = buildDailyRateEvolution(projects);

        // Bench time: idle days between consecutive signed missions and their estimated cost
        double resolvedAverageRate = averageDailyRate != null ? averageDailyRate : 0;
        long[] bench = buildBenchStats(projects);

        return DashboardStatsDto.builder()
            .totalProjects(totalProjects != null ? totalProjects : 0)
            .averageDailyRate(averageDailyRate != null ? averageDailyRate : 0)
            .totalEstimatedRevenue(totalRevenue)
            .forecastRevenue(forecastRevenue)
            .activeProjects(activeProjects != null ? activeProjects : 0)
            .wonProjects(wonProjects != null ? wonProjects : 0)
            .lostProjects(lostProjects != null ? lostProjects : 0)
            .totalBenchDays(bench[0])
            .benchPeriods(bench[1])
            .estimatedBenchCost(bench[0] * resolvedAverageRate)
            .projectsByStatus(projectsByStatus)
            .projectsByWorkMode(projectsByWorkMode)
            .lostReasonsBreakdown(lostReasonsBreakdown)
            .dailyRateRanges(dailyRateRanges)
            .sourceRoi(sourceRoi)
            .dailyRateEvolution(dailyRateEvolution)
            .conversionFunnel(buildConversionFunnel(projects))
            .funnelBySource(buildFunnelBreakdown(projects,
                p -> p.getSource() != null ? p.getSource().getName() : null))
            .funnelByClientType(buildFunnelBreakdown(projects,
                p -> p.getMiddleman() != null ? "INTERMEDIARY" : "DIRECT"))
            .funnelByEsn(buildFunnelBreakdown(projects,
                p -> p.getMiddleman() != null ? p.getMiddleman().getCompanyName() : null))
            .build();
    }

    /**
     * Splits opportunities into groups by the given classifier and builds a conversion funnel for
     * each, so drop-off can be compared across sources, client types or ESNs. Opportunities the
     * classifier maps to {@code null} (e.g. no source/ESN) are skipped; groups are ordered by name.
     */
    private List<DashboardStatsDto.FunnelBreakdown> buildFunnelBreakdown(
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
     * opportunities are excluded (their drop stage is not tracked). Because statuses are ordered,
     * an opportunity at a later stage has passed every earlier one, so each stage counts every
     * opportunity at or beyond it; the conversion rate is relative to the first stage.
     */
    private List<DashboardStatsDto.ConversionFunnelStage> buildConversionFunnel(List<Project> projects) {
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
     * Average asked vs obtained (agreed) daily rate per year of start date, ordered
     * chronologically, so a freelance can see how negotiated rates trend over time.
     */
    private List<DashboardStatsDto.DailyRateEvolution> buildDailyRateEvolution(List<Project> projects) {
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
     * Computes idle ("bench") time between consecutive signed missions: missions are the WON
     * projects that carry a start date and duration, ordered chronologically. A gap is counted
     * only when a mission starts after the latest end seen so far, so overlapping or nested
     * missions never produce negative bench. Returns {@code [totalBenchDays, benchPeriods]}.
     */
    private long[] buildBenchStats(List<Project> projects) {
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
     * Ranks each opportunity source by how many of its opportunities turned into signed
     * (WON) contracts. Sorted by signed contracts, then conversion rate, then name.
     */
    private List<DashboardStatsDto.SourceRoi> buildSourceRoiRanking(List<Project> projects) {
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
