package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardRemindersTest extends DashboardStatsTestSupport {

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
}
