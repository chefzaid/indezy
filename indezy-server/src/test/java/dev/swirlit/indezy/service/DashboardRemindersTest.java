package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.StepStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardRemindersTest extends DashboardStatsTestSupport {

    @Test
    void upcomingInterviews_ShouldListThePlannedStepsOfTheComingWeekSoonestFirst() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 9, 0);
        Project mission = activeOpportunity(4L, ProjectStatus.INTERVIEW, now);
        mission.setRole("Java developer");
        InterviewStep later = step(1L, mission, "Client interview", StepStatus.PLANNED, now.plusDays(7));
        InterviewStep sooner = step(2L, mission, "Technical test", StepStatus.PLANNED, now.plusHours(2));
        InterviewStep past = step(3L, mission, "Call", StepStatus.PLANNED, now.minusMinutes(1));
        InterviewStep tooLate = step(4L, mission, "Final", StepStatus.PLANNED, now.plusDays(7).plusMinutes(1));
        InterviewStep unplanned = step(5L, mission, "To plan", StepStatus.TO_PLAN, now.plusDays(1));
        InterviewStep undated = step(6L, mission, "Undated", StepStatus.PLANNED, null);

        assertThat(DashboardReminders.buildUpcomingInterviews(List.of(later, sooner, past, tooLate, unplanned, undated), now))
            .extracting(DashboardStatsDto.UpcomingInterview::getStepId, DashboardStatsDto.UpcomingInterview::getTitle,
                DashboardStatsDto.UpcomingInterview::getRole, DashboardStatsDto.UpcomingInterview::getProjectId)
            .containsExactly(tuple(2L, "Technical test", "Java developer", 4L), tuple(1L, "Client interview", "Java developer", 4L));
    }

    private static InterviewStep step(Long id, Project project, String title, StepStatus status, LocalDateTime date) {
        InterviewStep step = new InterviewStep();
        step.setId(id);
        step.setProject(project);
        step.setTitle(title);
        step.setStatus(status);
        step.setDate(date);
        return step;
    }

    @Test
    void staleOpportunities_ShouldLeaveArchivedOnesOut() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 27, 12, 0);
        Project idle = activeOpportunity(1L, ProjectStatus.CONTACT, now.minusDays(20));
        Project archived = activeOpportunity(2L, ProjectStatus.CONTACT, now.minusDays(20));
        archived.setArchivedAt(now.minusDays(1));

        assertThat(DashboardReminders.buildStaleOpportunities(List.of(idle, archived), now, 7))
            .extracting(stale -> stale.getProjectId()).containsExactly(1L);
    }

    @Test
    void staleOpportunities_ShouldUseTheConfiguredPeriodAtTheExactBoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 27, 12, 0);
        Project old = activeOpportunity(1L, ProjectStatus.CONTACT, now.minusDays(20));
        Project boundary = activeOpportunity(2L, ProjectStatus.INTERVIEW, now.minusDays(7));
        Project recent = activeOpportunity(3L, ProjectStatus.OFFER, now.minusDays(7).plusSeconds(1));
        Project won = activeOpportunity(4L, ProjectStatus.WON, now.minusDays(20));
        Project lost = activeOpportunity(5L, ProjectStatus.LOST, now.minusDays(20));

        assertThat(DashboardReminders.buildStaleOpportunities(List.of(recent, boundary, old, won, lost), now, 7))
            .extracting(DashboardStatsDto.StaleOpportunity::getProjectId)
            .containsExactly(1L, 2L);
    }

    @Test
    void getDashboardStats_ShouldUseTheWorkspacePreferenceForStaleOpportunities() {
        when(freelanceRepository.findInactivityDays(1L)).thenReturn(Optional.of(7));
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(
            activeOpportunity(1L, ProjectStatus.CONTACT, LocalDateTime.now().minusDays(10))));

        assertThat(dashboardStatsService.getDashboardStats(1L, null).getStaleOpportunities())
            .extracting(DashboardStatsDto.StaleOpportunity::getProjectId).containsExactly(1L);
    }

    @Test
    void getDashboardStats_ShouldListSignedMissionsEndingSoon() {
        // Given signed missions ending in ~1 month (within the 6-week window) and others outside it.
        LocalDate today = LocalDate.now();
        Project endingA = wonMission(1L, today.minusMonths(1), 2);   // ends ~+1 month
        Project endingB = wonMission(2L, today, 1);                  // ends ~+1 month
        Project farFuture = wonMission(3L, today, 6);                // ends ~+6 months, excluded
        Project alreadyEnded = wonMission(4L, today.minusMonths(6), 2); // ended in the past, excluded
        Project pending = wonMission(5L, today, 1);
        pending.setStatus(ProjectStatus.INTERVIEW);                   // not signed, excluded
        Project noDates = wonMission(6L, null, null);                 // missing dates, excluded

        when(projectRepository.findByFreelanceId(1L))
            .thenReturn(List.of(endingA, endingB, farFuture, alreadyEnded, pending, noDates));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Then only the two soon-ending signed missions appear, soonest first, with their details.
        List<DashboardStatsDto.MissionEndingSoon> ending = stats.getMissionsEndingSoon();
        assertThat(ending).hasSize(2)
            .extracting(DashboardStatsDto.MissionEndingSoon::getProjectId)
            .containsExactlyInAnyOrder(1L, 2L);
        assertThat(ending).extracting(DashboardStatsDto.MissionEndingSoon::getDaysUntilEnd).isSorted();
        assertThat(ending).allSatisfy(m -> {
            assertThat(m.getDaysUntilEnd()).isBetween(0L, 42L);
            assertThat(m.getEndDate()).isNotNull();
            assertThat(m.getClientName()).isEqualTo("Test Company");
        });
    }

    @Test
    void getDashboardStats_ShouldListStaleActiveOpportunities() {
        // Given active opportunities with varying idle times plus closed/fresh ones to exclude.
        LocalDateTime now = LocalDateTime.now();
        Project veryStale = activeOpportunity(1L, ProjectStatus.CONTACT, now.minusDays(30));
        Project mildlyStale = activeOpportunity(2L, ProjectStatus.INTERVIEW, now.minusDays(15));
        Project fresh = activeOpportunity(3L, ProjectStatus.CONTACT, now.minusDays(3));   // too recent
        Project wonStale = activeOpportunity(4L, ProjectStatus.WON, now.minusDays(40));   // closed, excluded
        Project noActivity = activeOpportunity(5L, ProjectStatus.OFFER, null);            // no timestamp

        when(projectRepository.findByFreelanceId(1L))
            .thenReturn(List.of(veryStale, mildlyStale, fresh, wonStale, noActivity));

        // When
        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Then only stale active opportunities appear, most stale first, with their details.
        List<DashboardStatsDto.StaleOpportunity> stale = stats.getStaleOpportunities();
        assertThat(stale).extracting(DashboardStatsDto.StaleOpportunity::getProjectId)
            .containsExactly(1L, 2L);
        assertThat(stale.get(0).getDaysSinceActivity()).isGreaterThanOrEqualTo(stale.get(1).getDaysSinceActivity());
        assertThat(stale.get(0).getStatus()).isEqualTo("CONTACT");
        assertThat(stale.get(0).getClientName()).isEqualTo("Test Company");
    }

    @Test
    void getDashboardStats_ShouldSurfaceRenewalsWithinNoticePeriod() {
        LocalDate today = LocalDate.now();
        testProject.setStatus(ProjectStatus.WON);
        // Monthly renewals starting three months ago: the next one is always under a month out,
        // comfortably inside a 60-day notice window regardless of which day "today" is.
        testProject.setStartDate(today.minusMonths(3));
        testProject.setOrderRenewalInMonths(1);
        testFreelance.setNoticePeriodInDays(60);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject));
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        assertThat(stats.getUpcomingRenewals()).hasSize(1);
        assertThat(stats.getUpcomingRenewals().getFirst().getProjectId()).isEqualTo(1L);
        assertThat(stats.getUpcomingRenewals().getFirst().getClientName()).isEqualTo("Test Company");
        assertThat(stats.getUpcomingRenewals().getFirst().getDaysUntilRenewal()).isBetween(0L, 60L);
        assertThat(stats.getUpcomingRenewals().getFirst().getRenewalDate()).isAfterOrEqualTo(today);
    }

    @Test
    void getDashboardStats_ShouldNotSurfaceRenewalsBeyondNoticePeriod() {
        LocalDate today = LocalDate.now();
        testProject.setStatus(ProjectStatus.WON);
        // First renewal is three months out, well beyond a 30-day notice window.
        testProject.setStartDate(today);
        testProject.setOrderRenewalInMonths(3);
        testFreelance.setNoticePeriodInDays(30);

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject));
        when(freelanceRepository.findById(1L)).thenReturn(Optional.of(testFreelance));

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        assertThat(stats.getUpcomingRenewals()).isEmpty();
    }

    @Test
    void getDashboardStats_ShouldSurfaceOnThisDayItemsFromPreviousYears() {
        LocalDateTime now = LocalDateTime.now();
        testProject.setCreatedAt(now.minusYears(1));

        Contact contact = new Contact();
        contact.setId(5L);
        contact.setFirstName("Marie");
        contact.setLastName("Dubois");
        contact.setClient(testClient);
        contact.setCreatedAt(now.minusYears(2));

        Project recent = new Project();
        recent.setId(9L);
        recent.setRole("Recent Role");
        recent.setCreatedAt(now.minusMonths(2));

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject, recent));
        when(contactRepository.findByFreelanceId(1L)).thenReturn(List.of(contact));

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        // Two anniversaries (project 1y, contact 2y); the two-month-old project is excluded.
        assertThat(stats.getOnThisDay()).hasSize(2);
        assertThat(stats.getOnThisDay().getFirst().getType()).isEqualTo("PROJECT");
        assertThat(stats.getOnThisDay().getFirst().getId()).isEqualTo(1L);
        assertThat(stats.getOnThisDay().getFirst().getYearsAgo()).isEqualTo(1);
        assertThat(stats.getOnThisDay().get(1).getType()).isEqualTo("CONTACT");
        assertThat(stats.getOnThisDay().get(1).getLabel()).isEqualTo("Marie Dubois");
        assertThat(stats.getOnThisDay().get(1).getSubLabel()).isEqualTo("Test Company");
        assertThat(stats.getOnThisDay().get(1).getYearsAgo()).isEqualTo(2);
    }

    @Test
    void getDashboardStats_ShouldNotSurfaceOnThisDayItemsOutsideTheWindow() {
        testProject.setCreatedAt(LocalDateTime.now().minusYears(1).minusDays(10));

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(testProject));
        when(contactRepository.findByFreelanceId(1L)).thenReturn(List.of());

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        assertThat(stats.getOnThisDay()).isEmpty();
    }

    @Test
    void getDashboardStats_ShouldSurfaceContactsDormantForSixMonthsOrMore() {
        Contact dormant = new Contact();
        dormant.setId(3L);
        dormant.setFirstName("Old");
        dormant.setLastName("Contact");
        dormant.setClient(testClient);
        dormant.setUpdatedAt(LocalDateTime.now().minusMonths(8));

        Contact active = new Contact();
        active.setId(4L);
        active.setFirstName("Fresh");
        active.setLastName("Contact");
        active.setUpdatedAt(LocalDateTime.now().minusMonths(1));

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of());
        when(contactRepository.findByFreelanceId(1L)).thenReturn(List.of(dormant, active));

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        assertThat(stats.getDormantContacts()).hasSize(1);
        assertThat(stats.getDormantContacts().getFirst().getId()).isEqualTo(3L);
        assertThat(stats.getDormantContacts().getFirst().getName()).isEqualTo("Old Contact");
        assertThat(stats.getDormantContacts().getFirst().getClientName()).isEqualTo("Test Company");
        assertThat(stats.getDormantContacts().getFirst().getMonthsSinceActivity()).isGreaterThanOrEqualTo(6L);
    }

    @Test
    void getDashboardStats_ShouldCountARecentExchangeAsActivity() {
        Contact written = new Contact();
        written.setId(5L);
        written.setFirstName("Written");
        written.setUpdatedAt(LocalDateTime.now().minusMonths(9));
        Contact silent = new Contact();
        silent.setId(6L);
        silent.setFirstName("Silent");
        silent.setUpdatedAt(LocalDateTime.now().minusMonths(9));

        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of());
        when(contactRepository.findByFreelanceId(1L)).thenReturn(List.of(written, silent));
        when(contactInteractionService.lastContactedInWorkspace(1L)).thenReturn(Map.of(
            5L, LocalDate.now().minusDays(10), 6L, LocalDate.now().minusYears(2)));

        DashboardStatsDto stats = dashboardStatsService.getDashboardStats(1L, null);

        assertThat(stats.getDormantContacts()).extracting(DashboardStatsDto.DormantContact::getId).containsExactly(6L);
        assertThat(stats.getDormantContacts().getFirst().getMonthsSinceActivity()).isEqualTo(9L);
    }
}
