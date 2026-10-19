package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ActivityReportDto;
import dev.swirlit.indezy.dto.ActivityReportLinkDto;
import dev.swirlit.indezy.dto.ActivityReportUpdateRequest;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ActivityReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}/activity-reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Activity Reports", description = "Monthly activity reports (CRA) of accepted missions")
public class ActivityReportController {

    private final ActivityReportService activityReportService;
    private final AccessGuard accessGuard;

    @Operation(summary = "List activity reports", description = "The mission's saved reports, latest month first")
    @GetMapping
    public List<ActivityReportDto> getReports(@PathVariable Long projectId) {
        log.debug("GET /projects/{}/activity-reports", projectId);
        accessGuard.requireProject(projectId);
        return activityReportService.getReports(projectId);
    }

    @Operation(summary = "Get a month's activity report",
        description = "The saved report, or an empty draft with the month's public holidays and business days")
    @GetMapping("/{year}/{month}")
    public ActivityReportDto getReport(@PathVariable Long projectId, @PathVariable @Min(2000) @Max(2100) int year,
                                       @PathVariable @Min(1) @Max(12) int month) {
        log.debug("GET /projects/{}/activity-reports/{}/{}", projectId, year, month);
        accessGuard.requireProject(projectId);
        return activityReportService.getReport(projectId, YearMonth.of(year, month));
    }

    @Operation(summary = "Save a month's activity report",
        description = "Replaces the recorded days and comment of a draft or rejected report; accepted missions only")
    @PutMapping("/{year}/{month}")
    public ActivityReportDto saveReport(@PathVariable Long projectId, @PathVariable @Min(2000) @Max(2100) int year,
                                        @PathVariable @Min(1) @Max(12) int month,
                                        @Valid @RequestBody ActivityReportUpdateRequest request) {
        log.debug("PUT /projects/{}/activity-reports/{}/{}", projectId, year, month);
        accessGuard.requireProject(projectId);
        return activityReportService.saveReport(projectId, YearMonth.of(year, month), request);
    }

    @Operation(summary = "Submit for validation",
        description = "Locks the report and returns its validation link token, shown only once and valid for 30 days")
    @PostMapping("/{year}/{month}/submit")
    public ActivityReportLinkDto submit(@PathVariable Long projectId, @PathVariable @Min(2000) @Max(2100) int year,
                                        @PathVariable @Min(1) @Max(12) int month) {
        log.debug("POST /projects/{}/activity-reports/{}/{}/submit", projectId, year, month);
        accessGuard.requireProject(projectId);
        return activityReportService.submit(projectId, YearMonth.of(year, month));
    }

    @Operation(summary = "Renew the validation link", description = "Replaces the link of a submitted report; the old one stops working")
    @PostMapping("/{year}/{month}/link")
    public ActivityReportLinkDto renewLink(@PathVariable Long projectId, @PathVariable @Min(2000) @Max(2100) int year,
                                           @PathVariable @Min(1) @Max(12) int month) {
        log.debug("POST /projects/{}/activity-reports/{}/{}/link", projectId, year, month);
        accessGuard.requireProject(projectId);
        return activityReportService.renewLink(projectId, YearMonth.of(year, month));
    }

    @Operation(summary = "Reopen a report", description = "Back to draft: revokes the link and clears any approval or rejection")
    @PostMapping("/{year}/{month}/reopen")
    public ActivityReportDto reopen(@PathVariable Long projectId, @PathVariable @Min(2000) @Max(2100) int year,
                                    @PathVariable @Min(1) @Max(12) int month) {
        log.debug("POST /projects/{}/activity-reports/{}/{}/reopen", projectId, year, month);
        accessGuard.requireProject(projectId);
        return activityReportService.reopen(projectId, YearMonth.of(year, month));
    }

    @Operation(summary = "Download the report as PDF", description = "Printed with the template of the mission's ESN or client")
    @GetMapping("/{year}/{month}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long projectId, @PathVariable @Min(2000) @Max(2100) int year,
                                              @PathVariable @Min(1) @Max(12) int month) {
        log.debug("GET /projects/{}/activity-reports/{}/{}/pdf", projectId, year, month);
        accessGuard.requireProject(projectId);
        return PdfResponses.attachment(activityReportService.renderPdf(projectId, YearMonth.of(year, month)));
    }
}
