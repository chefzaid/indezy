package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.YearlyReviewDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.YearlyReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/review")
@RequiredArgsConstructor
@Validated
@Tag(name = "Projects", description = "Opportunities and missions")
public class YearlyReviewController {

    private final YearlyReviewService reviewService;
    private final AccessGuard accessGuard;

    @Operation(summary = "A workspace's year: missions, revenue, opportunities, skills and network")
    @GetMapping("/{freelanceId}")
    public YearlyReviewDto review(@PathVariable Long freelanceId, @RequestParam @Min(2000) @Max(2100) int year) {
        accessGuard.requireFreelance(freelanceId);
        return reviewService.review(freelanceId, year);
    }

    @Operation(summary = "The yearly review as a PDF, in French or English")
    @GetMapping("/{freelanceId}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long freelanceId, @RequestParam @Min(2000) @Max(2100) int year,
                                      @RequestParam(defaultValue = "FR") DocumentLanguage language) {
        accessGuard.requireFreelance(freelanceId);
        return PdfResponses.attachment(reviewService.renderPdf(freelanceId, year, language));
    }
}
