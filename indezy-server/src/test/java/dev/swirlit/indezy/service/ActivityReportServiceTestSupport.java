package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ActivityDayDto;
import dev.swirlit.indezy.dto.ActivityReportDecisionRequest;
import dev.swirlit.indezy.dto.ActivityReportUpdateRequest;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.pdf.ActivityReportPdfRenderer;
import dev.swirlit.indezy.repository.ActivityReportRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Mission, ESN, mocks and helpers shared by the activity report service tests; the clock is fixed at {@link #NOW}. */
abstract class ActivityReportServiceTestSupport {

    static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");
    static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    static final long PROJECT_ID = 7L;

    final ActivityReportRepository reportRepository = mock(ActivityReportRepository.class);
    final ProjectRepository projectRepository = mock(ProjectRepository.class);
    final ActivityReportPdfRenderer pdfRenderer = mock(ActivityReportPdfRenderer.class);
    final ActivityReportService service = new ActivityReportService(reportRepository, projectRepository, pdfRenderer,
        Clock.fixed(NOW, ZoneOffset.UTC));

    Project project;
    Client esn;

    @BeforeEach
    void setUp() {
        Freelance freelance = new Freelance();
        freelance.setId(1L);
        freelance.setFirstName("Zoé");
        freelance.setLastName("Martin");
        Client client = new Client();
        client.setCompanyName("Société Générale");
        esn = new Client();
        esn.setCompanyName("Capgemini");
        project = new Project();
        project.setId(PROJECT_ID);
        project.setRole("Développeur Java / Spring Boot");
        project.setStatus(ProjectStatus.WON);
        project.setDailyRate(600);
        project.setStartDate(LocalDate.of(2026, 9, 1));
        project.setDurationInMonths(6);
        project.setFreelance(freelance);
        project.setClient(client);
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(reportRepository.save(any(ActivityReport.class))).thenAnswer(invocation -> {
            ActivityReport report = invocation.getArgument(0);
            if (report.getId() == null) {
                report.setId(40L);
            }
            return report;
        });
    }

    ActivityReport report(ActivityReportStatus status) {
        ActivityReport report = new ActivityReport();
        report.setId(40L);
        report.setProject(project);
        report.setReportYear(2026);
        report.setReportMonth(11);
        report.setStatus(status);
        when(reportRepository.findByProjectIdAndReportYearAndReportMonth(PROJECT_ID, 2026, 11)).thenReturn(Optional.of(report));
        return report;
    }

    ActivityReport submittedWithToken(String token, Instant expiresAt) {
        ActivityReport report = report(ActivityReportStatus.SUBMITTED);
        report.setValidationTokenHash(ActivityReportService.hash(token));
        report.setValidationExpiresAt(expiresAt);
        when(reportRepository.findByValidationTokenHash(ActivityReportService.hash(token))).thenReturn(Optional.of(report));
        return report;
    }

    static ActivityDayDto day(int dayOfMonth, ActivityDayType type, boolean halfDay) {
        return new ActivityDayDto(NOVEMBER.atDay(dayOfMonth), type, halfDay);
    }

    static ActivityReportUpdateRequest request(String comment, ActivityDayDto... days) {
        ActivityReportUpdateRequest request = new ActivityReportUpdateRequest();
        request.setComment(comment);
        request.setDays(new ArrayList<>(List.of(days)));
        return request;
    }

    static ActivityReportDecisionRequest decision(boolean approved, String name, String comment) {
        ActivityReportDecisionRequest request = new ActivityReportDecisionRequest();
        request.setApproved(approved);
        request.setValidatorName(name);
        request.setComment(comment);
        return request;
    }
}
