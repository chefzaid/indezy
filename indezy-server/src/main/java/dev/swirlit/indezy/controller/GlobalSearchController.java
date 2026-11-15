package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.GlobalSearchResultDto;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.GlobalSearchService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Search the workspace from anywhere")
public class GlobalSearchController {

    private final GlobalSearchService searchService;
    private final AccessGuard accessGuard;

    @Operation(summary = "Search the workspace's opportunities, clients, contacts and notes, and the network's members and posts, at once",
        description = "Accents and case aside, every word typed must appear in one field; at most 8 hits of each kind")
    @GetMapping
    public GlobalSearchResultDto search(@RequestParam String q, @RequestParam(required = false) Long freelanceId) {
        // An API token reads the workspace only, never the member network.
        Long member = SecurityUtils.isApiTokenRequest() ? null : SecurityUtils.findCurrentUserId().orElse(null);
        return searchService.search(accessGuard.requireWorkspace(freelanceId), member, q);
    }
}
