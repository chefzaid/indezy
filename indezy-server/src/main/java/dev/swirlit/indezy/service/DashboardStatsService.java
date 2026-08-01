package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final FreelanceRepository freelanceRepository;
    private final ContactRepository contactRepository;

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

        // Notice period drives how early upcoming contract renewals are surfaced
        int noticePeriodInDays = freelanceRepository.findById(freelanceId)
            .map(Freelance::getNoticePeriodInDays)
            .filter(days -> days > 0)
            .orElse(DEFAULT_NOTICE_PERIOD_DAYS);

        List<Contact> contacts = contactRepository.findByFreelanceId(freelanceId);

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
            .missionsEndingSoon(buildMissionsEndingSoon(projects, LocalDate.now()))
            .staleOpportunities(buildStaleOpportunities(projects, LocalDateTime.now()))
            .upcomingRenewals(buildUpcomingRenewals(projects, LocalDate.now(), noticePeriodInDays))
            .onThisDay(buildOnThisDay(projects, contacts, LocalDate.now()))
            .dormantContacts(buildDormantContacts(contacts, LocalDateTime.now()))
            .skillTrends(buildSkillTrends(projects))
            .build();
    }

    /** How many top skills to surface in the demand ranking. */
    private static final int TOP_SKILLS_LIMIT = 12;

    /**
     * Ranks the skills/technologies appearing across opportunities' tech stacks by how often
     * they occur, alongside the average daily rate of the opportunities requiring each. Tech
     * stacks are split on commas and grouped case-insensitively. Most in-demand first.
     */
    private List<DashboardStatsDto.SkillTrend> buildSkillTrends(List<Project> projects) {
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
                    skill.toLowerCase(java.util.Locale.ROOT), key -> new SkillAccumulator(skill));
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

    /** How many days either side of the anniversary still counts as "on this day". */
    private static final int ANNIVERSARY_WINDOW_DAYS = 3;

    /**
     * Surfaces opportunities and contacts created around today's calendar date in a previous
     * year, as a re-engagement nudge. Only prior calendar years are considered; most recent
     * (fewest years ago) first.
     */
    private List<DashboardStatsDto.OnThisDayItem> buildOnThisDay(
            List<Project> projects, List<Contact> contacts, LocalDate today) {
        List<DashboardStatsDto.OnThisDayItem> items = new ArrayList<>();
        for (Project project : projects) {
            addIfAnniversary(items, project.getCreatedAt(), today, "PROJECT", project.getId(),
                project.getRole(), project.getClient() != null ? project.getClient().getCompanyName() : null);
        }
        for (Contact contact : contacts) {
            addIfAnniversary(items, contact.getCreatedAt(), today, "CONTACT", contact.getId(),
                contact.getFullName(), contact.getClient() != null ? contact.getClient().getCompanyName() : null);
        }
        items.sort(Comparator.comparingInt(DashboardStatsDto.OnThisDayItem::getYearsAgo));
        return items;
    }

    private void addIfAnniversary(List<DashboardStatsDto.OnThisDayItem> items, LocalDateTime createdAt,
            LocalDate today, String type, Long id, String label, String subLabel) {
        if (createdAt == null) {
            return;
        }
        LocalDate created = createdAt.toLocalDate();
        int yearsAgo = today.getYear() - created.getYear();
        if (yearsAgo < 1) {
            return;
        }
        LocalDate anniversary = anniversaryInYear(created, today.getYear());
        if (Math.abs(ChronoUnit.DAYS.between(anniversary, today)) > ANNIVERSARY_WINDOW_DAYS) {
            return;
        }
        items.add(DashboardStatsDto.OnThisDayItem.builder()
            .type(type)
            .id(id)
            .label(label)
            .subLabel(subLabel)
            .date(created)
            .yearsAgo(yearsAgo)
            .build());
    }

    /** The date's anniversary in the given year, mapping Feb 29 to Feb 28 in non-leap years. */
    private LocalDate anniversaryInYear(LocalDate date, int year) {
        if (date.getMonthValue() == 2 && date.getDayOfMonth() == 29 && !java.time.Year.isLeap(year)) {
            return LocalDate.of(year, 2, 28);
        }
        return date.withYear(year);
    }

    /** Contacts untouched for at least this many months are surfaced as re-engagement nudges. */
    private static final int DORMANT_THRESHOLD_MONTHS = 6;

    /**
     * Lists contacts with no recorded activity (their last update) for at least
     * {@link #DORMANT_THRESHOLD_MONTHS} months, so the freelance can reconnect before the
     * relationship goes cold. Most dormant first.
     */
    private List<DashboardStatsDto.DormantContact> buildDormantContacts(List<Contact> contacts, LocalDateTime now) {
        List<DashboardStatsDto.DormantContact> dormant = new ArrayList<>();
        for (Contact contact : contacts) {
            LocalDateTime lastActivity = contact.getUpdatedAt() != null
                ? contact.getUpdatedAt() : contact.getCreatedAt();
            if (lastActivity == null) {
                continue;
            }
            long monthsSinceActivity = ChronoUnit.MONTHS.between(lastActivity, now);
            if (monthsSinceActivity < DORMANT_THRESHOLD_MONTHS) {
                continue;
            }
            dormant.add(DashboardStatsDto.DormantContact.builder()
                .id(contact.getId())
                .name(contact.getFullName())
                .clientName(contact.getClient() != null ? contact.getClient().getCompanyName() : null)
                .monthsSinceActivity(monthsSinceActivity)
                .build());
        }
        dormant.sort(Comparator.comparingLong(DashboardStatsDto.DormantContact::getMonthsSinceActivity).reversed());
        return dormant;
    }

    /** Notice period applied when the freelance has not configured one. */
    private static final int DEFAULT_NOTICE_PERIOD_DAYS = 30;

    /**
     * Lists signed (WON) missions whose next order-renewal date ({@code startDate} advanced by
     * whole multiples of {@code orderRenewalInMonths}) falls between today and the freelance's
     * notice period, so the renewal can be confirmed or notice given in time. Soonest first.
     */
    private List<DashboardStatsDto.UpcomingRenewal> buildUpcomingRenewals(
            List<Project> projects, LocalDate today, int noticePeriodInDays) {
        List<DashboardStatsDto.UpcomingRenewal> renewals = new ArrayList<>();
        for (Project project : projects) {
            if (!ProjectStatus.WON.equals(project.getStatus())
                || project.getStartDate() == null
                || project.getOrderRenewalInMonths() == null
                || project.getOrderRenewalInMonths() <= 0) {
                continue;
            }
            LocalDate renewalDate = project.getStartDate().plusMonths(project.getOrderRenewalInMonths());
            while (renewalDate.isBefore(today)) {
                renewalDate = renewalDate.plusMonths(project.getOrderRenewalInMonths());
            }
            long daysUntilRenewal = ChronoUnit.DAYS.between(today, renewalDate);
            if (daysUntilRenewal > noticePeriodInDays) {
                continue;
            }
            renewals.add(DashboardStatsDto.UpcomingRenewal.builder()
                .projectId(project.getId())
                .role(project.getRole())
                .clientName(project.getClient() != null ? project.getClient().getCompanyName() : null)
                .renewalDate(renewalDate)
                .daysUntilRenewal(daysUntilRenewal)
                .build());
        }
        renewals.sort(Comparator.comparingLong(DashboardStatsDto.UpcomingRenewal::getDaysUntilRenewal));
        return renewals;
    }

    /** Active opportunities idle for at least this many days are surfaced for follow-up. */
    private static final int STALE_THRESHOLD_DAYS = 14;

    /**
     * Lists active (in-pipeline, i.e. neither WON nor LOST) opportunities whose last update is at
     * least {@link #STALE_THRESHOLD_DAYS} days old, so they can be followed up on or archived
     * before going cold. Most stale first.
     */
    private List<DashboardStatsDto.StaleOpportunity> buildStaleOpportunities(List<Project> projects, LocalDateTime now) {
        List<DashboardStatsDto.StaleOpportunity> stale = new ArrayList<>();
        for (Project project : projects) {
            ProjectStatus status = project.getStatus();
            if (status == null || status == ProjectStatus.WON || status == ProjectStatus.LOST
                || project.getUpdatedAt() == null) {
                continue;
            }
            long daysSinceActivity = ChronoUnit.DAYS.between(project.getUpdatedAt(), now);
            if (daysSinceActivity < STALE_THRESHOLD_DAYS) {
                continue;
            }
            stale.add(DashboardStatsDto.StaleOpportunity.builder()
                .projectId(project.getId())
                .role(project.getRole())
                .clientName(project.getClient() != null ? project.getClient().getCompanyName() : null)
                .status(status.name())
                .daysSinceActivity(daysSinceActivity)
                .build());
        }
        stale.sort(Comparator.comparingLong(DashboardStatsDto.StaleOpportunity::getDaysSinceActivity).reversed());
        return stale;
    }

    /** Signed missions ending within this many weeks are surfaced as prospection reminders. */
    private static final int ENDING_SOON_WEEKS = 6;

    /**
     * Lists signed (WON) missions whose end date ({@code startDate + durationInMonths}) falls
     * between today and {@link #ENDING_SOON_WEEKS} weeks out, so the freelance can restart
     * prospection before the bench. Already-ended missions are excluded; soonest first.
     */
    private List<DashboardStatsDto.MissionEndingSoon> buildMissionsEndingSoon(List<Project> projects, LocalDate today) {
        long threshold = ENDING_SOON_WEEKS * 7L;
        List<DashboardStatsDto.MissionEndingSoon> ending = new ArrayList<>();
        for (Project project : projects) {
            if (!ProjectStatus.WON.equals(project.getStatus())
                || project.getStartDate() == null || project.getDurationInMonths() == null) {
                continue;
            }
            LocalDate endDate = project.getStartDate().plusMonths(project.getDurationInMonths());
            long daysUntilEnd = ChronoUnit.DAYS.between(today, endDate);
            if (daysUntilEnd < 0 || daysUntilEnd > threshold) {
                continue;
            }
            ending.add(DashboardStatsDto.MissionEndingSoon.builder()
                .projectId(project.getId())
                .role(project.getRole())
                .clientName(project.getClient() != null ? project.getClient().getCompanyName() : null)
                .endDate(endDate)
                .daysUntilEnd(daysUntilEnd)
                .build());
        }
        ending.sort(Comparator.comparingLong(DashboardStatsDto.MissionEndingSoon::getDaysUntilEnd));
        return ending;
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
