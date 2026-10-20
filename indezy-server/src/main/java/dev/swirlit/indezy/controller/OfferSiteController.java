package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.OfferSiteExclusionDto;
import dev.swirlit.indezy.dto.OfferSiteExclusionRequest;
import dev.swirlit.indezy.dto.OfferSiteHealthDto;
import dev.swirlit.indezy.dto.OfferSiteRefreshDto;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.scraping.OfferSiteRefresher;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.OfferSiteHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The health of the job boards of the offer feed: how each one's collection goes, which ones the caller's workspace
 * keeps out of its feed, and reading one on request. {@code freelanceId} is only read when security checks are off.
 */
@RestController
@RequestMapping("/offers/sites")
@RequiredArgsConstructor
@Tag(name = "Offer sites", description = "Collection health of the job boards of the offer feed")
public class OfferSiteController {

    private final OfferSiteHealthService healthService;
    private final OfferSiteRefresher refresher;
    private final AccessGuard accessGuard;

    @Operation(summary = "List the job boards with their collection's health, and the boards left out")
    @GetMapping("/health")
    public OfferSiteHealthDto health(@RequestParam(required = false) Long freelanceId) {
        return healthService.health(accessGuard.resolveFreelanceId(freelanceId));
    }

    @Operation(summary = "Keep a job board out of the workspace's offer feed, or bring it back")
    @PutMapping("/{site}/exclusion")
    public OfferSiteExclusionDto setExcluded(@PathVariable OfferSite site, @Valid @RequestBody OfferSiteExclusionRequest request,
                                             @RequestParam(required = false) Long freelanceId) {
        return healthService.setExcluded(accessGuard.requireWorkspace(freelanceId), site, request.excluded());
    }

    @Operation(summary = "Read a job board now; at most once every 15 minutes, whoever asks")
    @PostMapping("/{site}/refresh")
    public ResponseEntity<OfferSiteRefreshDto> refresh(@PathVariable OfferSite site, @RequestParam(required = false) Long freelanceId) {
        accessGuard.requireWorkspace(freelanceId);
        return new ResponseEntity<>(new OfferSiteRefreshDto(site, refresher.refresh(site)), HttpStatus.ACCEPTED);
    }
}
