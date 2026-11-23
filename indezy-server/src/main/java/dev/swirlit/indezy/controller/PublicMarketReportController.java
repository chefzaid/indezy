package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.MarketReportDto;
import dev.swirlit.indezy.service.MarketReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The quarterly market report, open without an account. */
@RestController
@RequestMapping("/public/market-report")
@RequiredArgsConstructor
@Tag(name = "Market report", description = "Daily rates the feed's offers state, by quarter, skill, region and seniority")
public class PublicMarketReportController {

    private final MarketReportService reports;

    @Operation(summary = "The market report of a quarter", description = "The last complete quarter by default, as 2026-Q3")
    @GetMapping
    public MarketReportDto report(@RequestParam(required = false) String quarter) {
        return reports.report(quarter);
    }
}
