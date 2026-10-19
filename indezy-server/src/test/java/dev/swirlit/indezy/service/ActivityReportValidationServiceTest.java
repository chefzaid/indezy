package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ActivityReportDto;
import dev.swirlit.indezy.dto.PublicActivityReportDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.ActivityReport;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportLayout;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.pdf.ActivityReportDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What a validation link shows and accepts, and the PDF printed with the client's template. */
class ActivityReportValidationServiceTest extends ActivityReportServiceTestSupport {

    @Test
    void getPublicReport_ShouldShowTheMissionAndMonthWithoutIdsOrMoney() {
        project.setMiddleman(esn);
        esn.setActivityReportShowsLocation(false);
        ActivityReport submitted = submittedWithToken("valid-token", NOW.plusSeconds(60));
        submitted.setDays(List.of(new ActivityDay(LocalDate.of(2026, 11, 2), ActivityDayType.ONSITE, false)));

        PublicActivityReportDto view = service.getPublicReport("valid-token");

        assertThat(view.getConsultantName()).isEqualTo("Zoé Martin");
        assertThat(view.getClientName()).isEqualTo("Société Générale");
        assertThat(view.getMiddlemanName()).isEqualTo("Capgemini");
        assertThat(view.getMissionRole()).isEqualTo("Développeur Java / Spring Boot");
        assertThat(view.isShowsLocation()).isFalse();
        ActivityReportDto report = view.getReport();
        assertThat(report.getWorkedDays()).isEqualTo(1);
        assertThat(report.getStatus()).isEqualTo(ActivityReportStatus.SUBMITTED);
        assertThat(report.getId()).isNull();
        assertThat(report.getProjectId()).isNull();
        assertThat(report.getDailyRate()).isNull();
        assertThat(report.getAmount()).isNull();
        assertThat(report.getLinkExpiresAt()).isNull();
    }

    @Test
    void getPublicReport_WithAFinalClientOnly_ShouldUseItsTemplateAndNoIntermediary() {
        submittedWithToken("valid-token", NOW.plusSeconds(60));

        PublicActivityReportDto view = service.getPublicReport("valid-token");

        assertThat(view.getMiddlemanName()).isNull();
        assertThat(view.isShowsLocation()).isTrue();
    }

    @Test
    void getPublicReport_WithAnExpiredLink_ShouldBeNotFound() {
        submittedWithToken("expired-token", NOW);

        assertThatThrownBy(() -> service.getPublicReport("expired-token"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("This validation link is invalid or has expired");
    }

    @Test
    void getPublicReport_WithAnUnknownToken_ShouldBeNotFound() {
        when(reportRepository.findByValidationTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicReport("unknown")).isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void getPublicReport_WithoutToken_ShouldBeNotFoundWithoutALookup(String token) {
        assertThatThrownBy(() -> service.getPublicReport(token)).isInstanceOf(ResourceNotFoundException.class);
        verify(reportRepository, never()).findByValidationTokenHash(any());
    }

    @Test
    void getPublicReport_WithAnOversizedToken_ShouldBeNotFoundWithoutALookup() {
        assertThatThrownBy(() -> service.getPublicReport("x".repeat(129))).isInstanceOf(ResourceNotFoundException.class);
        verify(reportRepository, never()).findByValidationTokenHash(any());
    }

    @Test
    void decide_Approving_ShouldValidateWithTheTrimmedNameAndTime() {
        ActivityReport submitted = submittedWithToken("valid-token", NOW.plusSeconds(60));

        PublicActivityReportDto view = service.decide("valid-token", decision(true, "  Jean Dupont ", "  "));

        assertThat(submitted.getStatus()).isEqualTo(ActivityReportStatus.VALIDATED);
        assertThat(submitted.getValidatorName()).isEqualTo("Jean Dupont");
        assertThat(submitted.getDecidedAt()).isEqualTo(NOW);
        assertThat(submitted.getDecisionComment()).isNull();
        assertThat(view.getReport().getStatus()).isEqualTo(ActivityReportStatus.VALIDATED);
        assertThat(view.getReport().getValidatorName()).isEqualTo("Jean Dupont");
    }

    @Test
    void decide_RejectingWithAReason_ShouldRejectTheReport() {
        ActivityReport submitted = submittedWithToken("valid-token", NOW.plusSeconds(60));

        service.decide("valid-token", decision(false, "Jean Dupont", "Le 11 est férié"));

        assertThat(submitted.getStatus()).isEqualTo(ActivityReportStatus.REJECTED);
        assertThat(submitted.getDecisionComment()).isEqualTo("Le 11 est férié");
    }

    @Test
    void decide_RejectingWithoutReason_ShouldBeRefused() {
        ActivityReport submitted = submittedWithToken("valid-token", NOW.plusSeconds(60));

        assertThatThrownBy(() -> service.decide("valid-token", decision(false, "Jean Dupont", " ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A rejection needs a reason");
        assertThat(submitted.getStatus()).isEqualTo(ActivityReportStatus.SUBMITTED);
    }

    @Test
    void decide_WhenAlreadyDecided_ShouldBeRefused() {
        ActivityReport validated = submittedWithToken("valid-token", NOW.plusSeconds(60));
        validated.setStatus(ActivityReportStatus.VALIDATED);

        assertThatThrownBy(() -> service.decide("valid-token", decision(false, "Someone", "Changed my mind")))
            .isInstanceOf(InvalidStateException.class);
        assertThat(validated.getStatus()).isEqualTo(ActivityReportStatus.VALIDATED);
    }

    @Test
    void renderPdf_ShouldPrintWithTheEsnTemplateAndAnAsciiFileName() {
        project.setMiddleman(esn);
        esn.setActivityReportTitle("Relevé d'activité");
        esn.setActivityReportLayout(ActivityReportLayout.CALENDAR_GRID);
        esn.setActivityReportLanguage(DocumentLanguage.EN);
        ActivityReport validated = report(ActivityReportStatus.VALIDATED);
        validated.setValidatorName("Jean Dupont");
        validated.setDays(List.of(new ActivityDay(LocalDate.of(2026, 11, 2), ActivityDayType.REMOTE, true)));
        when(pdfRenderer.render(any())).thenReturn(new byte[] {37, 80, 68, 70});

        ActivityReportService.PdfFile file = service.renderPdf(PROJECT_ID, NOVEMBER);

        assertThat(file.fileName()).isEqualTo("CRA-2026-11-developpeur-java-spring-boot.pdf");
        assertThat(file.content()).containsExactly(37, 80, 68, 70);
        ArgumentCaptor<ActivityReportDocument> captor = ArgumentCaptor.forClass(ActivityReportDocument.class);
        verify(pdfRenderer).render(captor.capture());
        ActivityReportDocument document = captor.getValue();
        assertThat(document.template().title()).isEqualTo("Relevé d'activité");
        assertThat(document.template().layout()).isEqualTo(ActivityReportLayout.CALENDAR_GRID);
        assertThat(document.template().language()).isEqualTo(DocumentLanguage.EN);
        assertThat(document.consultantName()).isEqualTo("Zoé Martin");
        assertThat(document.middlemanName()).isEqualTo("Capgemini");
        assertThat(document.month()).isEqualTo(NOVEMBER);
        assertThat(document.workedDays()).isEqualTo(0.5);
        assertThat(document.validatorName()).isEqualTo("Jean Dupont");
    }

    @Test
    void renderPublicPdf_ShouldUseTheFinalClientTemplateAndKeepShortFileNamesForEmptyRoles() {
        project.setRole("€€€");
        submittedWithToken("valid-token", NOW.plusSeconds(60));
        when(pdfRenderer.render(any())).thenReturn(new byte[] {1});

        ActivityReportService.PdfFile file = service.renderPublicPdf("valid-token");

        assertThat(file.fileName()).isEqualTo("CRA-2026-11.pdf");
        ArgumentCaptor<ActivityReportDocument> captor = ArgumentCaptor.forClass(ActivityReportDocument.class);
        verify(pdfRenderer).render(captor.capture());
        assertThat(captor.getValue().template().layout()).isEqualTo(ActivityReportLayout.DAILY_TABLE);
        assertThat(captor.getValue().middlemanName()).isNull();
    }

    @Test
    void renderPdf_WithAVeryLongRole_ShouldCutTheFileName() {
        project.setRole("Architecte solutions cloud et data senior pour la transformation");
        report(ActivityReportStatus.DRAFT);
        when(pdfRenderer.render(any())).thenReturn(new byte[] {1});

        assertThat(service.renderPdf(PROJECT_ID, NOVEMBER).fileName())
            .isEqualTo("CRA-2026-11-architecte-solutions-cloud-et-data.pdf");
    }

}
