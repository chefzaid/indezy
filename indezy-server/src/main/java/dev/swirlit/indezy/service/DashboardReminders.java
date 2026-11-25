package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.dto.OfferRateMarketDto;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.StepStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.Map;

/**
 * Pure computations for the dashboard's time-based nudges: stale opportunities, missions ending
 * soon, upcoming renewals, "on this day" anniversaries, dormant contacts and the interviews of the coming week.
 */
final class DashboardReminders {

    private static final int ENDING_SOON_WEEKS = 6;
    private static final int ANNIVERSARY_WINDOW_DAYS = 3;
    private static final int DORMANT_THRESHOLD_MONTHS = 6;
    static final int UPCOMING_INTERVIEW_DAYS = 7;

    private DashboardReminders() {
    }

    /**
     * Lists active (in-pipeline, i.e. neither accepted nor refused, nor archived) opportunities whose last update is at
     * least the account's configured inactivity period old, so they can be followed up on or archived before
     * going cold. Most stale first.
     */
    static List<DashboardStatsDto.StaleOpportunity> buildStaleOpportunities(
            List<Project> projects, LocalDateTime now, int inactivityDays) {
        List<DashboardStatsDto.StaleOpportunity> stale = new ArrayList<>();
        for (Project project : projects) {
            ProjectStatus status = project.getStatus();
            if (status == null || status == ProjectStatus.WON || status.isRefused() ||
                project.getUpdatedAt() == null || project.getArchivedAt() != null) {
                continue;
            }
            long daysSinceActivity = ChronoUnit.DAYS.between(project.getUpdatedAt().atZone(ZoneId.systemDefault()),
                now.atZone(ZoneId.systemDefault()));
            if (daysSinceActivity >= inactivityDays) {
                stale.add(DashboardStatsDto.StaleOpportunity.builder()
                    .projectId(project.getId())
                    .role(project.getRole())
                    .clientName(project.getClient() != null ? project.getClient().getCompanyName() : null)
                    .status(status.name())
                    .daysSinceActivity(daysSinceActivity)
                    .build());
            }
        }
        stale.sort(Comparator.comparingLong(DashboardStatsDto.StaleOpportunity::getDaysSinceActivity).reversed());
        return stale;
    }

    /** The bench planner looks this many weeks ahead. */
    static final int BENCH_WEEKS = 12;

    /**
     * This week's goal against the bench: none while a running mission has no end or ends beyond {@value #BENCH_WEEKS}
     * weeks; otherwise opportunities to add this week, more the nearer the end (8 within 4 weeks or between missions, 5
     * within 8, 3 beyond), two fewer for each at the interview or offer stage, never under 1.
     */
    static DashboardStatsDto.BenchPlan buildBenchPlan(List<Project> projects, LocalDate today) {
        Project ending = null;
        LocalDate endDate = null;
        boolean hasMission = false;
        for (Project project : projects) {
            if (project.getStatus() != ProjectStatus.WON || project.getArchivedAt() != null || project.getStartDate() == null ||
                project.getStartDate().isAfter(today)) {
                continue;
            }
            LocalDate last = project.lastDay();
            if (last == null) {
                // A running open-ended mission: no bench in sight.
                return null;
            }
            if (last.isBefore(today)) {
                continue;
            }
            hasMission = true;
            if (endDate == null || last.isAfter(endDate)) {
                ending = project;
                endDate = last;
            }
        }
        long weeksLeft = hasMission ? (ChronoUnit.DAYS.between(today, endDate) + 6) / 7 : 0;
        if (weeksLeft > BENCH_WEEKS) {
            return null;
        }
        LocalDateTime monday = today.minusDays(today.getDayOfWeek().getValue() - 1L).atStartOfDay();
        long active = 0;
        long advanced = 0;
        long added = 0;
        LocalDateTime latest = null;
        for (Project project : projects) {
            if (project.getArchivedAt() != null || project.getStatus() == ProjectStatus.WON) {
                continue;
            }
            if (project.getStatus() == ProjectStatus.CONTACT || project.getStatus() == ProjectStatus.INTERVIEW ||
                project.getStatus() == ProjectStatus.OFFER) {
                active++;
                advanced += project.getStatus() == ProjectStatus.CONTACT ? 0 : 1;
            }
            LocalDateTime created = project.getCreatedAt();
            if (created != null) {
                added += created.isBefore(monday) ? 0 : 1;
                latest = latest == null || created.isAfter(latest) ? created : latest;
            }
        }
        long base = weeksLeft <= 4 ? 8 : weeksLeft <= 8 ? 5 : 3;
        return DashboardStatsDto.BenchPlan.builder()
            .projectId(ending == null ? null : ending.getId())
            .role(ending == null ? null : ending.getRole())
            .endDate(endDate)
            .weeksLeft(weeksLeft)
            .activeOpportunities(active)
            .advancedOpportunities(advanced)
            .addedThisWeek(added)
            .daysSinceLastAdded(latest == null ? null : ChronoUnit.DAYS.between(latest.toLocalDate(), today))
            .weeklyGoal(Math.max(1, base - 2 * advanced))
            .build();
    }

    /**
     * Lists signed (WON) missions whose end date ({@code startDate + durationInMonths}) falls
     * between today and {@link #ENDING_SOON_WEEKS} weeks out, so the freelance can restart
     * prospection before the bench. Already-ended missions are excluded; soonest first.
     */
    static List<DashboardStatsDto.MissionEndingSoon> buildMissionsEndingSoon(List<Project> projects, LocalDate today) {
        long threshold = ENDING_SOON_WEEKS * 7L;
        List<DashboardStatsDto.MissionEndingSoon> ending = new ArrayList<>();
        for (Project project : projects) {
            // A mission already ended from its page is not ending soon.
            if (!ProjectStatus.WON.equals(project.getStatus()) || project.getEndedOn() != null ||
                project.getStartDate() == null || project.getDurationInMonths() == null) {
                continue;
            }
            LocalDate endDate = project.getStartDate().plusMonths(project.getDurationInMonths());
            long daysUntilEnd = ChronoUnit.DAYS.between(today, endDate);
            if (daysUntilEnd >= 0 && daysUntilEnd <= threshold) {
                ending.add(DashboardStatsDto.MissionEndingSoon.builder()
                    .projectId(project.getId())
                    .role(project.getRole())
                    .clientName(project.getClient() != null ? project.getClient().getCompanyName() : null)
                    .endDate(endDate)
                    .daysUntilEnd(daysUntilEnd)
                    .build());
            }
        }
        ending.sort(Comparator.comparingLong(DashboardStatsDto.MissionEndingSoon::getDaysUntilEnd));
        return ending;
    }

    /**
     * Lists signed (WON) missions whose next order-renewal date ({@code startDate} advanced by whole
     * multiples of {@code orderRenewalInMonths}) falls between today and the freelance's notice
     * period, so the renewal can be confirmed or notice given in time. Soonest first.
     */
    static List<DashboardStatsDto.UpcomingRenewal> buildUpcomingRenewals(
            List<Project> projects, LocalDate today, int noticePeriodInDays) {
        return buildUpcomingRenewals(projects, today, noticePeriodInDays, project -> null);
    }

    /** As above, each renewal with its rate against the market, as {@code market} gives it, to review the rate in time. */
    static List<DashboardStatsDto.UpcomingRenewal> buildUpcomingRenewals(
            List<Project> projects, LocalDate today, int noticePeriodInDays, Function<Project, OfferRateMarketDto> market) {
        List<DashboardStatsDto.UpcomingRenewal> renewals = new ArrayList<>();
        for (Project project : projects) {
            if (!ProjectStatus.WON.equals(project.getStatus()) ||
                project.getStartDate() == null ||
                project.getOrderRenewalInMonths() == null ||
                project.getOrderRenewalInMonths() <= 0) {
                continue;
            }
            LocalDate renewalDate = project.getStartDate().plusMonths(project.getOrderRenewalInMonths());
            while (renewalDate.isBefore(today)) {
                renewalDate = renewalDate.plusMonths(project.getOrderRenewalInMonths());
            }
            long daysUntilRenewal = ChronoUnit.DAYS.between(today, renewalDate);
            if (daysUntilRenewal <= noticePeriodInDays) {
                renewals.add(DashboardStatsDto.UpcomingRenewal.builder()
                    .projectId(project.getId())
                    .role(project.getRole())
                    .clientName(project.getClient() != null ? project.getClient().getCompanyName() : null)
                    .renewalDate(renewalDate)
                    .daysUntilRenewal(daysUntilRenewal)
                    .dailyRate(project.getDailyRate())
                    .market(market.apply(project))
                    .build());
            }
        }
        renewals.sort(Comparator.comparingLong(DashboardStatsDto.UpcomingRenewal::getDaysUntilRenewal));
        return renewals;
    }

    /**
     * Surfaces opportunities and contacts created around today's calendar date in a previous year,
     * as a re-engagement nudge. Only prior calendar years are considered; most recent first.
     */
    static List<DashboardStatsDto.OnThisDayItem> buildOnThisDay(
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

    private static void addIfAnniversary(List<DashboardStatsDto.OnThisDayItem> items, LocalDateTime createdAt,
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
    private static LocalDate anniversaryInYear(LocalDate date, int year) {
        if (date.getMonthValue() == 2 && date.getDayOfMonth() == 29 && !Year.isLeap(year)) {
            return LocalDate.of(year, 2, 28);
        }
        return date.withYear(year);
    }

    /** The planned interview steps from {@code now} to seven days later, soonest first. */
    static List<DashboardStatsDto.UpcomingInterview> buildUpcomingInterviews(List<InterviewStep> steps, LocalDateTime now) {
        LocalDateTime until = now.plusDays(UPCOMING_INTERVIEW_DAYS);
        return steps.stream()
            .filter(step -> step.getStatus() == StepStatus.PLANNED && step.getDate() != null &&
                !step.getDate().isBefore(now) && !step.getDate().isAfter(until))
            .sorted(Comparator.comparing(InterviewStep::getDate))
            .map(step -> DashboardStatsDto.UpcomingInterview.builder()
                .stepId(step.getId())
                .projectId(step.getProject().getId())
                .title(step.getTitle())
                .role(step.getProject().getRole())
                .clientName(step.getProject().getClient() != null ? step.getProject().getClient().getCompanyName() : null)
                .date(step.getDate())
                .build())
            .toList();
    }

    /**
     * Lists contacts with no activity (their last update or recorded exchange, in {@code lastContacted}) for at least
     * {@link #DORMANT_THRESHOLD_MONTHS} months, so the freelance can reconnect before the
     * relationship goes cold; none who asked not to be contacted or was anonymized. Most dormant first.
     */
    static List<DashboardStatsDto.DormantContact> buildDormantContacts(List<Contact> contacts, Map<Long, LocalDate> lastContacted,
                                                                       LocalDateTime now) {
        List<DashboardStatsDto.DormantContact> dormant = new ArrayList<>();
        for (Contact contact : contacts) {
            // Not someone to reconnect with: they asked not to be contacted, or their data was anonymized.
            if (Boolean.TRUE.equals(contact.getDoNotContact()) || contact.getAnonymizedAt() != null) {
                continue;
            }
            LocalDateTime lastActivity = contact.getUpdatedAt() != null ?
                contact.getUpdatedAt() : contact.getCreatedAt();
            LocalDate lastExchange = lastContacted.get(contact.getId());
            if (lastExchange != null && (lastActivity == null || lastExchange.atStartOfDay().isAfter(lastActivity))) {
                lastActivity = lastExchange.atStartOfDay();
            }
            if (lastActivity == null) {
                continue;
            }
            long monthsSinceActivity = ChronoUnit.MONTHS.between(lastActivity, now);
            if (monthsSinceActivity >= DORMANT_THRESHOLD_MONTHS) {
                dormant.add(DashboardStatsDto.DormantContact.builder()
                    .id(contact.getId())
                    .name(contact.getFullName())
                    .clientName(contact.getClient() != null ? contact.getClient().getCompanyName() : null)
                    .monthsSinceActivity(monthsSinceActivity)
                    .build());
            }
        }
        dormant.sort(Comparator.comparingLong(DashboardStatsDto.DormantContact::getMonthsSinceActivity).reversed());
        return dormant;
    }
}
