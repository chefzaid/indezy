package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure computations for the dashboard's time-based nudges: stale opportunities, missions ending
 * soon, upcoming renewals, "on this day" anniversaries and dormant contacts.
 */
final class DashboardReminders {

    private static final int STALE_THRESHOLD_DAYS = 14;
    private static final int ENDING_SOON_WEEKS = 6;
    private static final int ANNIVERSARY_WINDOW_DAYS = 3;
    private static final int DORMANT_THRESHOLD_MONTHS = 6;

    private DashboardReminders() {
    }

    /**
     * Lists active (in-pipeline, i.e. neither WON nor LOST) opportunities whose last update is at
     * least {@link #STALE_THRESHOLD_DAYS} days old, so they can be followed up on or archived before
     * going cold. Most stale first.
     */
    static List<DashboardStatsDto.StaleOpportunity> buildStaleOpportunities(List<Project> projects, LocalDateTime now) {
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

    /**
     * Lists signed (WON) missions whose end date ({@code startDate + durationInMonths}) falls
     * between today and {@link #ENDING_SOON_WEEKS} weeks out, so the freelance can restart
     * prospection before the bench. Already-ended missions are excluded; soonest first.
     */
    static List<DashboardStatsDto.MissionEndingSoon> buildMissionsEndingSoon(List<Project> projects, LocalDate today) {
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
     * Lists signed (WON) missions whose next order-renewal date ({@code startDate} advanced by whole
     * multiples of {@code orderRenewalInMonths}) falls between today and the freelance's notice
     * period, so the renewal can be confirmed or notice given in time. Soonest first.
     */
    static List<DashboardStatsDto.UpcomingRenewal> buildUpcomingRenewals(
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

    /**
     * Lists contacts with no recorded activity (their last update) for at least
     * {@link #DORMANT_THRESHOLD_MONTHS} months, so the freelance can reconnect before the
     * relationship goes cold. Most dormant first.
     */
    static List<DashboardStatsDto.DormantContact> buildDormantContacts(List<Contact> contacts, LocalDateTime now) {
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
}
