package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.ActivityReportRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfessionalKilometresServiceTest {

    private final ActivityReportRepository reports = mock(ActivityReportRepository.class);
    private final CommuteService commutes = mock(CommuteService.class);
    private final ProfessionalKilometresService service = new ProfessionalKilometresService(reports, commutes);

    private static Project mission(long id, String client, String role) {
        Project project = new Project();
        project.setId(id);
        project.setRole(role);
        if (client != null) {
            Client company = new Client();
            company.setCompanyName(client);
            project.setClient(company);
        }
        return project;
    }

    private static ActivityReport report(Project mission, int year, int month, ActivityReportStatus status, int onsite, int remote,
                                         boolean halfOnsite) {
        ActivityReport report = new ActivityReport();
        report.setProject(mission);
        report.setReportYear(year);
        report.setReportMonth(month);
        report.setStatus(status);
        List<ActivityDay> days = new ArrayList<>();
        for (int day = 1; day <= onsite; day++) {
            days.add(new ActivityDay(LocalDate.of(year, month, day), ActivityDayType.ONSITE, false));
        }
        for (int day = 1; day <= remote; day++) {
            days.add(new ActivityDay(LocalDate.of(year, month, 10 + day), ActivityDayType.REMOTE, false));
        }
        if (halfOnsite) {
            days.add(new ActivityDay(LocalDate.of(year, month, 28), ActivityDayType.ONSITE, true));
        }
        report.setDays(days);
        return report;
    }

    @Test
    void multipliesTheDaysOnSiteByTheRoundTripPerMonthAndMission() {
        Project bank = mission(1, "=Banque", "Lead Java");
        Project unknown = mission(2, null, "Audit");
        when(commutes.drivingMeters(bank)).thenReturn(Optional.of(28_350));
        when(commutes.drivingMeters(unknown)).thenReturn(Optional.empty());
        when(reports.findByProjectFreelanceIdOrderByReportYearDescReportMonthDesc(1L)).thenReturn(List.of(
            report(bank, 2026, 3, ActivityReportStatus.VALIDATED, 10, 8, true),
            report(unknown, 2026, 2, ActivityReportStatus.VALIDATED, 4, 0, false),
            report(bank, 2026, 2, ActivityReportStatus.VALIDATED, 12, 0, false),
            report(bank, 2026, 4, ActivityReportStatus.SUBMITTED, 20, 0, false),
            report(bank, 2025, 12, ActivityReportStatus.VALIDATED, 20, 0, false)));

        assertThat(service.csv(1L, 2026, DocumentLanguage.FR)).isEqualTo(
            "Mois;Client;Mission;Jours sur site;Aller-retour (km);Kilomètres\r\n" +
                "2026-02;'=Banque;Lead Java;12;56,7;680,4\r\n" +
                "2026-02;;Audit;4;;\r\n" +
                "2026-03;'=Banque;Lead Java;10,5;56,7;595,4\r\n" +
                "Total;;;;;1275,8\r\n");
        // Each mission's distance is looked up once.
        verify(commutes, times(1)).drivingMeters(bank);
    }

    @Test
    void writesEnglishHeadersAndNumbers() {
        when(reports.findByProjectFreelanceIdOrderByReportYearDescReportMonthDesc(1L)).thenReturn(List.of());
        assertThat(service.csv(1L, 2026, DocumentLanguage.EN))
            .isEqualTo("Month;Client;Mission;Days on site;Round trip (km);Kilometres\r\nTotal;;;;;0\r\n");
    }
}
