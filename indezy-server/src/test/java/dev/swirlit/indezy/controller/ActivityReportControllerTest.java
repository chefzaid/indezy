package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ActivityReportDto;
import dev.swirlit.indezy.dto.ActivityReportLinkDto;
import dev.swirlit.indezy.dto.ActivityReportUpdateRequest;
import dev.swirlit.indezy.dto.PublicActivityReportDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.service.ActivityReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP contract of the activity report endpoints, the freelance's ones and the public validation link's. */
@SpringBootTest
@AutoConfigureWebMvc
@WithMockUser
@ActiveProfiles("test")
class ActivityReportControllerTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final String BASE = "/projects/7/activity-reports";
    private static final String TOKEN_HEADER = "X-Report-Token";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private ActivityReportService activityReportService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void getReports_ShouldListTheMissionsReports() throws Exception {
        when(activityReportService.getReports(7L)).thenReturn(List.of(report(ActivityReportStatus.VALIDATED)));

        mockMvc.perform(get(BASE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("VALIDATED"))
            .andExpect(jsonPath("$[0].year").value(2026));
    }

    @Test
    void getReport_ShouldAnswerTheMonthsReport() throws Exception {
        when(activityReportService.getReport(7L, NOVEMBER)).thenReturn(report(ActivityReportStatus.DRAFT));

        mockMvc.perform(get(BASE + "/2026/11"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.month").value(11))
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void getReport_WithAnInvalidMonthOrYear_ShouldBeABadRequest() throws Exception {
        mockMvc.perform(get(BASE + "/2026/13")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.month").exists());
        mockMvc.perform(get(BASE + "/1999/1")).andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/2026/0")).andExpect(status().isBadRequest());
        verify(activityReportService, never()).getReport(any(), any());
    }

    @Test
    void saveReport_ShouldPassTheDaysToTheService() throws Exception {
        when(activityReportService.saveReport(eq(7L), eq(NOVEMBER), any(ActivityReportUpdateRequest.class)))
            .thenReturn(report(ActivityReportStatus.DRAFT));

        mockMvc.perform(put(BASE + "/2026/11").contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"Formation\",\"days\":[{\"date\":\"2026-11-02\",\"type\":\"ONSITE\",\"halfDay\":true}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void saveReport_WithoutDaysOrWithAnIncompleteDay_ShouldBeABadRequest() throws Exception {
        mockMvc.perform(put(BASE + "/2026/11").contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"x\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.days").value("Days are required"));
        mockMvc.perform(put(BASE + "/2026/11").contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":[{\"date\":\"2026-11-02\",\"halfDay\":null}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors['days[0].type']").value("Day type is required"));
        mockMvc.perform(put(BASE + "/2026/11").contentType(MediaType.APPLICATION_JSON)
                .content("{\"days\":[{\"date\":\"2026-11-02\",\"type\":\"HOLIDAY\"}]}"))
            .andExpect(status().isBadRequest());
        verify(activityReportService, never()).saveReport(any(), any(), any());
    }

    @Test
    void saveReport_OnALockedReport_ShouldBeAConflict() throws Exception {
        when(activityReportService.saveReport(eq(7L), eq(NOVEMBER), any()))
            .thenThrow(new InvalidStateException("The activity report is submitted or validated; reopen it to change it"));

        mockMvc.perform(put(BASE + "/2026/11").contentType(MediaType.APPLICATION_JSON).content("{\"days\":[]}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(containsString("reopen it")));
    }

    @Test
    void submitAndRenewLink_ShouldAnswerTheTokenOnce() throws Exception {
        ActivityReportLinkDto link = new ActivityReportLinkDto("token-value", Instant.parse("2026-12-03T10:00:00Z"),
            report(ActivityReportStatus.SUBMITTED));
        when(activityReportService.submit(7L, NOVEMBER)).thenReturn(link);
        when(activityReportService.renewLink(7L, NOVEMBER)).thenReturn(link);

        mockMvc.perform(post(BASE + "/2026/11/submit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("token-value"))
            .andExpect(jsonPath("$.expiresAt").value("2026-12-03T10:00:00Z"))
            .andExpect(jsonPath("$.report.status").value("SUBMITTED"));
        mockMvc.perform(post(BASE + "/2026/11/link"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("token-value"));
    }

    @Test
    void reopen_ShouldAnswerTheDraft() throws Exception {
        when(activityReportService.reopen(7L, NOVEMBER)).thenReturn(report(ActivityReportStatus.DRAFT));

        mockMvc.perform(post(BASE + "/2026/11/reopen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void downloadPdf_ShouldSendAnUncachedAttachment() throws Exception {
        when(activityReportService.renderPdf(7L, NOVEMBER))
            .thenReturn(new ActivityReportService.PdfFile("CRA-2026-11-developer.pdf", new byte[] {37, 80, 68, 70}));

        mockMvc.perform(get(BASE + "/2026/11/pdf"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"CRA-2026-11-developer.pdf\""))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().bytes(new byte[] {37, 80, 68, 70}));
    }

    @Test
    void downloadPdf_WithoutSavedReport_ShouldBeNotFound() throws Exception {
        when(activityReportService.renderPdf(7L, NOVEMBER)).thenThrow(new ResourceNotFoundException("No activity report"));

        mockMvc.perform(get(BASE + "/2026/11/pdf")).andExpect(status().isNotFound());
    }

    @Test
    void publicReport_ShouldReadTheTokenFromItsHeader() throws Exception {
        PublicActivityReportDto view = new PublicActivityReportDto();
        view.setMissionRole("Developer");
        view.setReport(report(ActivityReportStatus.SUBMITTED));
        when(activityReportService.getPublicReport("token-value")).thenReturn(view);

        mockMvc.perform(get("/public/activity-reports").header(TOKEN_HEADER, "token-value"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.missionRole").value("Developer"))
            .andExpect(jsonPath("$.report.status").value("SUBMITTED"));
    }

    @Test
    void publicReport_WithoutTheHeader_ShouldBeABadRequest() throws Exception {
        mockMvc.perform(get("/public/activity-reports")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/public/activity-reports/pdf")).andExpect(status().isBadRequest());
        verify(activityReportService, never()).getPublicReport(any());
    }

    @Test
    void publicReport_WithAnInvalidLink_ShouldBeNotFound() throws Exception {
        when(activityReportService.getPublicReport("expired"))
            .thenThrow(new ResourceNotFoundException("This validation link is invalid or has expired"));

        mockMvc.perform(get("/public/activity-reports").header(TOKEN_HEADER, "expired"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("This validation link is invalid or has expired"));
    }

    @Test
    void publicPdf_ShouldSendTheAttachment() throws Exception {
        when(activityReportService.renderPublicPdf("token-value"))
            .thenReturn(new ActivityReportService.PdfFile("CRA-2026-11.pdf", new byte[] {1}));

        mockMvc.perform(get("/public/activity-reports/pdf").header(TOKEN_HEADER, "token-value"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"CRA-2026-11.pdf\""));
    }

    @Test
    void decide_ShouldRecordTheDecision() throws Exception {
        PublicActivityReportDto view = new PublicActivityReportDto();
        view.setReport(report(ActivityReportStatus.VALIDATED));
        when(activityReportService.decide(eq("token-value"), any())).thenReturn(view);

        mockMvc.perform(post("/public/activity-reports/decision").header(TOKEN_HEADER, "token-value")
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"validatorName\":\"Jean Dupont\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.report.status").value("VALIDATED"));
    }

    @Test
    void decide_WithoutNameOrDecision_ShouldBeABadRequest() throws Exception {
        mockMvc.perform(post("/public/activity-reports/decision").header(TOKEN_HEADER, "token-value")
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true,\"validatorName\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.validatorName").value("Your name is required"));
        mockMvc.perform(post("/public/activity-reports/decision").header(TOKEN_HEADER, "token-value")
                .contentType(MediaType.APPLICATION_JSON).content("{\"validatorName\":\"Jean\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.approved").value("The decision is required"));
        verify(activityReportService, never()).decide(any(), any());
    }

    @Test
    void decide_WhenAlreadyDecided_ShouldBeAConflict() throws Exception {
        when(activityReportService.decide(eq("token-value"), any()))
            .thenThrow(new InvalidStateException("The activity report is not waiting for validation"));

        mockMvc.perform(post("/public/activity-reports/decision").header(TOKEN_HEADER, "token-value")
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":false,\"validatorName\":\"Jean\",\"comment\":\"No\"}"))
            .andExpect(status().isConflict());
    }

    private static ActivityReportDto report(ActivityReportStatus status) {
        ActivityReportDto dto = new ActivityReportDto();
        dto.setYear(2026);
        dto.setMonth(11);
        dto.setStatus(status);
        dto.setDays(List.of());
        return dto;
    }
}
