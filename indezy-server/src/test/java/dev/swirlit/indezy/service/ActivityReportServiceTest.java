package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ActivityDayDto;
import dev.swirlit.indezy.dto.ActivityReportDto;
import dev.swirlit.indezy.dto.ActivityReportLinkDto;
import dev.swirlit.indezy.dto.PublicHolidayDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.PublicHoliday;
import dev.swirlit.indezy.util.Tokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Recording, saving, submitting, renewing and reopening a mission's monthly activity reports. */
class ActivityReportServiceTest extends ActivityReportServiceTestSupport {

    @Test
    void getReport_WithoutSavedReport_ShouldAnswerTheMonthsCalendarAsAnEmptyDraft() {
        when(reportRepository.findByProjectIdAndReportYearAndReportMonth(PROJECT_ID, 2026, 11)).thenReturn(Optional.empty());

        ActivityReportDto report = service.getReport(PROJECT_ID, NOVEMBER);

        assertThat(report.getId()).isNull();
        assertThat(report.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(report.getStatus()).isEqualTo(ActivityReportStatus.DRAFT);
        assertThat(report.getDays()).isEmpty();
        assertThat(report.getPublicHolidays()).containsExactly(
            new PublicHolidayDto(LocalDate.of(2026, 11, 1), PublicHoliday.ALL_SAINTS),
            new PublicHolidayDto(LocalDate.of(2026, 11, 11), PublicHoliday.ARMISTICE));
        assertThat(report.getBusinessDays()).isEqualTo(20);
        assertThat(report.getMissionStart()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(report.getMissionEnd()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(report.getDailyRate()).isEqualTo(600);
        assertThat(report.getAmount()).isZero();
    }

    @Test
    void getReport_WithoutDurationOrRate_ShouldLeaveMissionEndAndAmountUnknown() {
        project.setDurationInMonths(null);
        project.setDailyRate(null);
        when(reportRepository.findByProjectIdAndReportYearAndReportMonth(PROJECT_ID, 2026, 11)).thenReturn(Optional.empty());

        ActivityReportDto report = service.getReport(PROJECT_ID, NOVEMBER);

        assertThat(report.getMissionEnd()).isNull();
        assertThat(report.getAmount()).isNull();
    }

    @Test
    void getReport_ForAnUnknownProject_ShouldBeNotFound() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getReport(99L, NOVEMBER)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void saveReport_ShouldCreateTheReportWithSortedDaysTotalsAndAmount() {
        when(reportRepository.findByProjectIdAndReportYearAndReportMonth(PROJECT_ID, 2026, 11)).thenReturn(Optional.empty());

        ActivityReportDto saved = service.saveReport(PROJECT_ID, NOVEMBER, request("  Formation le 4  ",
            day(4, ActivityDayType.REMOTE, true), day(2, ActivityDayType.ONSITE, false),
            day(3, ActivityDayType.ONSITE, false), day(5, ActivityDayType.ABSENCE, false)));

        assertThat(saved.getId()).isEqualTo(40L);
        assertThat(saved.getDays()).extracting(ActivityDayDto::getDate).containsExactly(
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5));
        assertThat(saved.getWorkedDays()).isEqualTo(2.5);
        assertThat(saved.getOnsiteDays()).isEqualTo(2);
        assertThat(saved.getRemoteDays()).isEqualTo(0.5);
        assertThat(saved.getAbsenceDays()).isEqualTo(1);
        assertThat(saved.getAmount()).isEqualTo(1500);
        assertThat(saved.getComment()).isEqualTo("Formation le 4");
        ArgumentCaptor<ActivityReport> captor = ArgumentCaptor.forClass(ActivityReport.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getProject()).isSameAs(project);
        assertThat(captor.getValue().getPeriod()).isEqualTo(NOVEMBER);
    }

    @Test
    void saveReport_WithoutHalfDayFlag_ShouldCountAFullDay() {
        report(ActivityReportStatus.DRAFT);

        ActivityReportDto saved = service.saveReport(PROJECT_ID, NOVEMBER,
            request(null, new ActivityDayDto(NOVEMBER.atDay(2), ActivityDayType.ONSITE, null)));

        assertThat(saved.getWorkedDays()).isEqualTo(1);
        assertThat(saved.getDays()).extracting(ActivityDayDto::getHalfDay).containsExactly(false);
    }

    @Test
    void saveReport_WithABlankComment_ShouldClearIt() {
        ActivityReport existing = report(ActivityReportStatus.DRAFT);
        existing.setComment("Old");

        ActivityReportDto saved = service.saveReport(PROJECT_ID, NOVEMBER, request("   "));

        assertThat(saved.getComment()).isNull();
        assertThat(saved.getDays()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ProjectStatus.class, names = "WON", mode = EnumSource.Mode.EXCLUDE)
    void saveReport_ForAnOpportunityNotYetAccepted_ShouldBeRefused(ProjectStatus status) {
        project.setStatus(status);
        when(reportRepository.findByProjectIdAndReportYearAndReportMonth(PROJECT_ID, 2026, 11)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveReport(PROJECT_ID, NOVEMBER, request(null)))
            .isInstanceOf(InvalidStateException.class)
            .hasMessageContaining("accepted mission");
        verify(reportRepository, never()).save(any());
    }

    @Test
    void saveReport_OnAnExistingReport_ShouldStillWorkWhenTheMissionLeftTheAcceptedStage() {
        report(ActivityReportStatus.DRAFT);
        project.setStatus(ProjectStatus.LOST);

        assertThat(service.saveReport(PROJECT_ID, NOVEMBER, request(null, day(2, ActivityDayType.ONSITE, false))).getWorkedDays())
            .isEqualTo(1);
    }

    @Test
    void saveReport_WithADayOfAnotherMonth_ShouldBeRejected() {
        report(ActivityReportStatus.DRAFT);
        ActivityDayDto october = new ActivityDayDto(LocalDate.of(2026, 10, 31), ActivityDayType.ONSITE, false);

        assertThatThrownBy(() -> service.saveReport(PROJECT_ID, NOVEMBER, request(null, october)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("month");
    }

    @Test
    void saveReport_WithTheSameDayTwice_ShouldBeRejected() {
        report(ActivityReportStatus.DRAFT);

        assertThatThrownBy(() -> service.saveReport(PROJECT_ID, NOVEMBER,
            request(null, day(2, ActivityDayType.ONSITE, true), day(2, ActivityDayType.REMOTE, true))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("only once");
    }

    @ParameterizedTest
    @EnumSource(value = ActivityReportStatus.class, names = {"SUBMITTED", "VALIDATED"})
    void saveReport_WhenSubmittedOrValidated_ShouldBeLocked(ActivityReportStatus status) {
        report(status);

        assertThatThrownBy(() -> service.saveReport(PROJECT_ID, NOVEMBER, request(null)))
            .isInstanceOf(InvalidStateException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void saveReport_AfterARejection_ShouldKeepTheReasonUntilTheNextSubmission() {
        ActivityReport rejected = report(ActivityReportStatus.REJECTED);
        rejected.setValidatorName("Jean Dupont");
        rejected.setDecisionComment("Le 11 est férié");

        ActivityReportDto saved = service.saveReport(PROJECT_ID, NOVEMBER, request(null, day(12, ActivityDayType.ONSITE, false)));

        assertThat(saved.getStatus()).isEqualTo(ActivityReportStatus.REJECTED);
        assertThat(saved.getDecisionComment()).isEqualTo("Le 11 est férié");
    }

    @Test
    void submit_ShouldLockTheReportAndIssueALinkWhoseTokenIsStoredOnlyAsAHash() {
        ActivityReport rejected = report(ActivityReportStatus.REJECTED);
        rejected.setValidatorName("Jean Dupont");
        rejected.setDecidedAt(NOW.minusSeconds(3600));
        rejected.setDecisionComment("Le 11 est férié");

        ActivityReportLinkDto link = service.submit(PROJECT_ID, NOVEMBER);

        assertThat(link.getToken()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(link.getExpiresAt()).isEqualTo(NOW.plus(ActivityReportService.LINK_VALIDITY));
        assertThat(rejected.getValidationTokenHash()).isEqualTo(Tokens.sha256Hex(link.getToken()))
            .isNotEqualTo(link.getToken()).hasSize(64);
        assertThat(rejected.getStatus()).isEqualTo(ActivityReportStatus.SUBMITTED);
        assertThat(rejected.getSubmittedAt()).isEqualTo(NOW);
        assertThat(rejected.getValidatorName()).isNull();
        assertThat(rejected.getDecisionComment()).isNull();
        assertThat(link.getReport().getStatus()).isEqualTo(ActivityReportStatus.SUBMITTED);
        assertThat(link.getReport().getLinkExpiresAt()).isEqualTo(link.getExpiresAt());
    }

    @Test
    void submit_WithoutSavedReport_ShouldBeNotFound() {
        when(reportRepository.findByProjectIdAndReportYearAndReportMonth(PROJECT_ID, 2026, 11)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(PROJECT_ID, NOVEMBER))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("No activity report for project 7 in 2026-11");
    }

    @Test
    void submit_WhenAlreadySubmitted_ShouldBeRefused() {
        report(ActivityReportStatus.SUBMITTED);

        assertThatThrownBy(() -> service.submit(PROJECT_ID, NOVEMBER)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void renewLink_ShouldReplaceTheTokenOfASubmittedReport() {
        ActivityReport submitted = report(ActivityReportStatus.SUBMITTED);
        submitted.setValidationTokenHash(Tokens.sha256Hex("old-token"));

        ActivityReportLinkDto link = service.renewLink(PROJECT_ID, NOVEMBER);

        assertThat(submitted.getValidationTokenHash()).isEqualTo(Tokens.sha256Hex(link.getToken()))
            .isNotEqualTo(Tokens.sha256Hex("old-token"));
        assertThat(submitted.getStatus()).isEqualTo(ActivityReportStatus.SUBMITTED);
    }

    @ParameterizedTest
    @EnumSource(value = ActivityReportStatus.class, names = "SUBMITTED", mode = EnumSource.Mode.EXCLUDE)
    void renewLink_WhenNotWaitingForValidation_ShouldBeRefused(ActivityReportStatus status) {
        report(status);

        assertThatThrownBy(() -> service.renewLink(PROJECT_ID, NOVEMBER)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void reopen_ShouldRevokeTheLinkAndForgetTheDecision() {
        ActivityReport validated = report(ActivityReportStatus.VALIDATED);
        validated.setSubmittedAt(NOW);
        validated.setValidationTokenHash(Tokens.sha256Hex("token"));
        validated.setValidationExpiresAt(NOW.plusSeconds(60));
        validated.setValidatorName("Jean Dupont");
        validated.setDecidedAt(NOW);

        ActivityReportDto reopened = service.reopen(PROJECT_ID, NOVEMBER);

        assertThat(reopened.getStatus()).isEqualTo(ActivityReportStatus.DRAFT);
        assertThat(reopened.getLinkExpiresAt()).isNull();
        assertThat(reopened.getValidatorName()).isNull();
        assertThat(reopened.getSubmittedAt()).isNull();
        assertThat(validated.getValidationTokenHash()).isNull();
        assertThat(validated.getDecidedAt()).isNull();
    }

    @Test
    void getReports_AndExport_ShouldMapEverySavedReport() {
        ActivityReport report = new ActivityReport();
        report.setId(3L);
        report.setProject(project);
        report.setReportYear(2026);
        report.setReportMonth(10);
        when(reportRepository.findByProjectIdOrderByReportYearDescReportMonthDesc(PROJECT_ID)).thenReturn(List.of(report));
        when(reportRepository.findByProjectFreelanceIdOrderByReportYearDescReportMonthDesc(1L)).thenReturn(List.of(report));

        assertThat(service.getReports(PROJECT_ID)).extracting(ActivityReportDto::getId).containsExactly(3L);
        assertThat(service.findByFreelanceId(1L)).extracting(ActivityReportDto::getMonth).containsExactly(10);
    }
}
