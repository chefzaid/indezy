package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ActivityReportDecisionRequest;
import dev.swirlit.indezy.dto.PublicActivityReportDto;
import dev.swirlit.indezy.service.ActivityReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * What a client or ESN reaches through an activity report's validation link, without an account. The link token
 * travels in the {@value #TOKEN_HEADER} header, never in the URL, so access logs and Referer headers do not keep it.
 */
@RestController
@RequestMapping("/public/activity-reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Activity Report Validation", description = "Public validation of activity reports through their link")
public class PublicActivityReportController {

    static final String TOKEN_HEADER = "X-Report-Token";
    private static final String TOKEN_DESCRIPTION = "Token of the validation link, from the part of the link after #";

    private final ActivityReportService activityReportService;

    @Operation(summary = "Open a report to validate", description = "The mission, consultant and month behind the link")
    @GetMapping
    public PublicActivityReportDto getReport(
            @Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(TOKEN_HEADER) String token) {
        log.debug("GET /public/activity-reports");
        return activityReportService.getPublicReport(token);
    }

    @Operation(summary = "Download the report to validate as PDF")
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(TOKEN_HEADER) String token) {
        log.debug("GET /public/activity-reports/pdf");
        return PdfResponses.attachment(activityReportService.renderPublicPdf(token));
    }

    @Operation(summary = "Approve or reject the report",
        description = "Signed with the validator's name; a rejection needs a reason. A report is decided once.")
    @PostMapping("/decision")
    public PublicActivityReportDto decide(
            @Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(TOKEN_HEADER) String token,
            @Valid @RequestBody ActivityReportDecisionRequest request) {
        log.debug("POST /public/activity-reports/decision");
        return activityReportService.decide(token, request);
    }
}
