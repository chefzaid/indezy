package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.dto.OfferFacetDto;
import dev.swirlit.indezy.dto.OfferPageDto;
import dev.swirlit.indezy.dto.OfferRegionDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.OfferSiteDto;
import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.dto.OfferStateUpdateRequest;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.OfferImportService;
import dev.swirlit.indezy.service.OfferSearchService;
import dev.swirlit.indezy.service.OfferStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The freelance offer feed: offers collected from the job boards, shared by every workspace, with what
 * the caller's workspace did with each. {@code freelanceId} is only read when security checks are off
 * (tests); otherwise the caller's own workspace is used.
 */
@RestController
@RequestMapping("/offers")
@RequiredArgsConstructor
@Validated
@Tag(name = "Offers", description = "Freelance missions collected from the job boards")
public class OfferController {

    private final OfferSearchService searchService;
    private final OfferStateService stateService;
    private final OfferImportService importService;
    private final AccessGuard accessGuard;

    @Operation(summary = "Search the offers, newest first by default")
    @GetMapping
    public OfferPageDto search(@Valid @ModelAttribute OfferSearchRequest request,
                               @RequestParam(required = false) Long freelanceId) {
        return searchService.search(accessGuard.resolveFreelanceId(freelanceId), request);
    }

    @Operation(summary = "Get an offer with its full description")
    @GetMapping("/{id}")
    public OfferDto get(@PathVariable Long id, @RequestParam(required = false) Long freelanceId) {
        return searchService.get(accessGuard.resolveFreelanceId(freelanceId), id);
    }

    @Operation(summary = "Save, hide or mark an offer as seen")
    @PutMapping("/{id}/state")
    public OfferStateDto updateState(@PathVariable Long id, @RequestBody OfferStateUpdateRequest request,
                                     @RequestParam(required = false) Long freelanceId) {
        return stateService.update(accessGuard.requireWorkspace(freelanceId), id, request);
    }

    @Operation(summary = "Add an offer to the pipeline as a new opportunity")
    @PostMapping("/{id}/import")
    public ResponseEntity<ProjectDto> importOffer(@PathVariable Long id, @RequestParam(required = false) Long freelanceId) {
        return new ResponseEntity<>(importService.importOffer(accessGuard.requireWorkspace(freelanceId), id), HttpStatus.CREATED);
    }

    @Operation(summary = "List the job boards of the feed and how their collection goes")
    @GetMapping("/sites")
    public List<OfferSiteDto> sites() {
        return searchService.sites();
    }

    @Operation(summary = "Suggest skills of open offers starting with the given text")
    @GetMapping("/skills")
    public List<OfferFacetDto> skills(@RequestParam(defaultValue = "") @Size(max = 60) String q) {
        return searchService.skills(q);
    }

    @Operation(summary = "List the French regions and departments the feed can be filtered on")
    @GetMapping("/regions")
    public List<OfferRegionDto> regions() {
        return searchService.regions();
    }
}
