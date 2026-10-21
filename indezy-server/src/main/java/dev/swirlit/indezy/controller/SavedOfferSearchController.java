package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.OfferAlertsDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.OfferSearchSuggestionsDto;
import dev.swirlit.indezy.dto.SavedOfferSearchDto;
import dev.swirlit.indezy.dto.SavedOfferSearchRequest;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.OfferSearchSuggestionService;
import dev.swirlit.indezy.service.SavedOfferSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The offer feed's saved searches, private to the caller's workspace. {@code freelanceId} is only read when security
 * checks are off (tests).
 */
@RestController
@RequestMapping("/offers/searches")
@RequiredArgsConstructor
@Tag(name = "Offers", description = "Freelance missions collected from the job boards")
public class SavedOfferSearchController {

    private final SavedOfferSearchService savedSearches;
    private final OfferSearchSuggestionService suggestions;
    private final AccessGuard accessGuard;

    @Operation(summary = "List the saved searches with the offers found for each since it was last opened")
    @GetMapping
    public List<SavedOfferSearchDto> list(@RequestParam(required = false) Long freelanceId) {
        return savedSearches.list(accessGuard.requireWorkspace(freelanceId));
    }

    @Operation(summary = "Save the feed's filters under a name")
    @PostMapping
    public ResponseEntity<SavedOfferSearchDto> create(@Valid @RequestBody SavedOfferSearchRequest request,
                                                      @RequestParam(required = false) Long freelanceId) {
        return new ResponseEntity<>(savedSearches.create(accessGuard.requireWorkspace(freelanceId), request), HttpStatus.CREATED);
    }

    @Operation(summary = "Open a saved search: its offers are no longer new")
    @PostMapping("/{id}/open")
    public SavedOfferSearchDto open(@PathVariable Long id, @RequestParam(required = false) Long freelanceId) {
        return savedSearches.open(accessGuard.requireWorkspace(freelanceId), id);
    }

    @Operation(summary = "Delete a saved search")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam(required = false) Long freelanceId) {
        savedSearches.delete(accessGuard.requireWorkspace(freelanceId), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Suggest searches from the profile and the pipeline, with the offers each finds now",
        description = "At most five, best first, leaving out the ones saved or dismissed; missing names the profile fields left empty")
    @GetMapping("/suggestions")
    public OfferSearchSuggestionsDto suggestions(@RequestParam(required = false) Long freelanceId) {
        return suggestions.suggestions(accessGuard.requireWorkspace(freelanceId));
    }

    @Operation(summary = "Dismiss a suggested search: the same filters are not suggested again")
    @PostMapping("/suggestions/dismissals")
    public ResponseEntity<Void> dismiss(@Valid @RequestBody OfferSearchRequest filters, @RequestParam(required = false) Long freelanceId) {
        suggestions.dismiss(accessGuard.requireWorkspace(freelanceId), filters);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Count the offers new for any saved search, for the badge on Offers")
    @GetMapping("/alerts")
    public OfferAlertsDto alerts(@RequestParam(required = false) Long freelanceId) {
        return savedSearches.alerts(accessGuard.requireWorkspace(freelanceId));
    }
}
