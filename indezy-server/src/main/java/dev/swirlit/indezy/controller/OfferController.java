package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.OfferApplicationRequest;
import dev.swirlit.indezy.dto.OfferBulkImportDto;
import dev.swirlit.indezy.dto.OfferBulkImportRequest;
import dev.swirlit.indezy.dto.OfferBulkStateRequest;
import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.dto.OfferFacetDto;
import dev.swirlit.indezy.dto.OfferLinkDto;
import dev.swirlit.indezy.dto.OfferLinkRequest;
import dev.swirlit.indezy.dto.OfferMarketDto;
import dev.swirlit.indezy.dto.OfferPageDto;
import dev.swirlit.indezy.dto.OfferPublisherDto;
import dev.swirlit.indezy.dto.OfferRegionDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.OfferSiteDto;
import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.dto.OfferStateUpdateRequest;
import dev.swirlit.indezy.dto.OfferTrendDto;
import dev.swirlit.indezy.dto.PostingImportRequest;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.OfferApplicationService;
import dev.swirlit.indezy.service.OfferBulkService;
import dev.swirlit.indezy.service.OfferImportService;
import dev.swirlit.indezy.service.OfferLinkService;
import dev.swirlit.indezy.service.OfferMarketService;
import dev.swirlit.indezy.service.OfferPublisherInsightService;
import dev.swirlit.indezy.service.OfferSearchService;
import dev.swirlit.indezy.service.OfferStateService;
import dev.swirlit.indezy.service.OfferTrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final OfferApplicationService applicationService;
    private final OfferBulkService bulkService;
    private final OfferLinkService linkService;
    private final OfferTrendService trendService;
    private final OfferPublisherInsightService publisherService;
    private final OfferMarketService marketService;
    private final AccessGuard accessGuard;

    @Operation(summary = "Search the offers, newest first by default")
    @GetMapping
    public OfferPageDto search(@Valid @ModelAttribute OfferSearchRequest request,
                               @RequestParam(required = false) Long freelanceId) {
        return searchService.search(accessGuard.resolveFreelanceId(freelanceId), request);
    }

    @Operation(summary = "Describe the market of the offers a search finds: rates, remote split, skills, intermediaries, offers per day")
    @GetMapping("/market")
    public OfferMarketDto market(@Valid @ModelAttribute OfferSearchRequest request,
                                 @RequestParam(required = false) Long freelanceId) {
        return marketService.snapshot(accessGuard.resolveFreelanceId(freelanceId), request);
    }

    @Operation(summary = "How the median daily rate and the open offers of a skill moved over 30, 90 and 365 days",
        description = "The profile's first skill without one, all of France without a region")
    @GetMapping("/market/trends")
    public OfferTrendDto marketTrends(@RequestParam(required = false) @Size(max = 60) String skill,
                                      @RequestParam(required = false) @Size(max = 3) String region,
                                      @RequestParam(required = false) Long freelanceId) {
        return trendService.trends(accessGuard.requireWorkspace(freelanceId), skill, region);
    }

    @Operation(summary = "What a publisher puts in the feed: its open missions, rates, republications, sites and offers",
        description = "The publisher named however it is written; 404 when none of its offers is collected")
    @GetMapping("/publishers/{name}")
    public OfferPublisherDto publisher(@PathVariable @Size(max = 200) String name, @RequestParam(required = false) Long freelanceId) {
        return publisherService.insights(accessGuard.requireWorkspace(freelanceId), name);
    }

    @Operation(summary = "Get an offer with its full description")
    @GetMapping("/{id}")
    public OfferDto get(@PathVariable Long id, @RequestParam(required = false) Long freelanceId) {
        return searchService.get(accessGuard.resolveFreelanceId(freelanceId), id);
    }

    @Operation(summary = "Save, hide or mark an offer as seen")
    @PutMapping("/{id}/state")
    public OfferStateDto updateState(@PathVariable Long id, @Valid @RequestBody OfferStateUpdateRequest request,
                                     @RequestParam(required = false) Long freelanceId) {
        return stateService.update(accessGuard.requireWorkspace(freelanceId), id, request);
    }

    @Operation(summary = "Save, hide or mark seen several offers at once")
    @PutMapping("/states")
    public List<OfferStateDto> updateStates(@Valid @RequestBody OfferBulkStateRequest request,
                                            @RequestParam(required = false) Long freelanceId) {
        return bulkService.updateAll(accessGuard.requireWorkspace(freelanceId), request);
    }

    @Operation(summary = "Add several offers to the pipeline, each mission once")
    @PostMapping("/import")
    public OfferBulkImportDto importOffers(@Valid @RequestBody OfferBulkImportRequest request,
                                           @RequestParam(required = false) Long freelanceId) {
        return bulkService.importAll(accessGuard.requireWorkspace(freelanceId), request);
    }

    @Operation(summary = "Find the offer of a link, with the opportunity it would make",
        description = "An offer not collected yet is read from its page, or its site is read (status READING)")
    @PostMapping("/link")
    public OfferLinkDto resolveLink(@Valid @RequestBody OfferLinkRequest request, @RequestParam(required = false) Long freelanceId) {
        return linkService.resolve(accessGuard.requireWorkspace(freelanceId), request.url());
    }

    @Operation(summary = "Add an offer to the pipeline as a new opportunity",
        description = "As the offer says, or as the body (the opportunity corrected in the project form) says: " +
            "no client is the publisher and no source the site, created when missing")
    @PostMapping("/{id}/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProjectDto> importOffer(@PathVariable Long id, @Valid @RequestBody(required = false) ProjectDto project,
                                                  @RequestParam(required = false) Long freelanceId) {
        Long workspace = accessGuard.requireWorkspace(freelanceId);
        if (project != null) {
            accessGuard.requireClientIfPresent(project.getClientId());
            accessGuard.requireClientIfPresent(project.getMiddlemanId());
            accessGuard.requireSourceIfPresent(project.getSourceId());
            accessGuard.requireSeasonIfPresent(project.getSeasonId());
        }
        return new ResponseEntity<>(importService.importOffer(workspace, id, project), HttpStatus.CREATED);
    }

    @Operation(summary = "Add a posting read from a site the feed does not collect to the pipeline",
        description = "As the body (the draft of its link, corrected in the project form) says: no client or source is the one " +
            "named, created when missing; 409 when its link is in the pipeline already")
    @PostMapping("/postings/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ProjectDto> importPosting(@Valid @RequestBody PostingImportRequest request,
                                                    @RequestParam(required = false) Long freelanceId) {
        Long workspace = accessGuard.requireWorkspace(freelanceId);
        ProjectDto project = request.getProject();
        accessGuard.requireClientIfPresent(project.getClientId());
        accessGuard.requireClientIfPresent(project.getMiddlemanId());
        accessGuard.requireSourceIfPresent(project.getSourceId());
        accessGuard.requireSeasonIfPresent(project.getSeasonId());
        return new ResponseEntity<>(importService.importPosting(workspace, request), HttpStatus.CREATED);
    }

    @Operation(summary = "Record that the workspace applied to an offer, adding it to the pipeline with its steps")
    @PostMapping("/{id}/application")
    public OfferStateDto apply(@PathVariable Long id, @Valid @RequestBody OfferApplicationRequest request,
                               @RequestParam(required = false) Long freelanceId) {
        return applicationService.apply(accessGuard.requireWorkspace(freelanceId), id, request);
    }

    @Operation(summary = "Forget an application; the opportunity stays in the pipeline")
    @DeleteMapping("/{id}/application")
    public OfferStateDto withdraw(@PathVariable Long id, @RequestParam(required = false) Long freelanceId) {
        return applicationService.withdraw(accessGuard.requireWorkspace(freelanceId), id);
    }

    @Operation(summary = "List the job boards of the feed and how their collection goes")
    @GetMapping("/sites")
    public List<OfferSiteDto> sites(@RequestParam(required = false) Long freelanceId) {
        return searchService.sites(accessGuard.resolveFreelanceId(freelanceId));
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
