package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkReviewDtos;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.NetworkReviewService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Members' reviews of the clients and ESNs they worked with, read and written from one of the workspace's clients. */
@RestController
@RequestMapping("/network/reviews/client/{clientId}")
@RequiredArgsConstructor
@Tag(name = "Network reviews", description = "Reviews of clients and ESNs by the members who worked with them")
public class NetworkReviewController {

    private final NetworkReviewService reviews;
    private final AccessGuard accessGuard;

    @Operation(summary = "What the network's members say of one of the workspace's clients")
    @GetMapping
    public NetworkReviewDtos.Company company(@PathVariable Long clientId) {
        return reviews.company(SecurityUtils.getCurrentUserId(), workspace(), clientId);
    }

    @Operation(summary = "Write or rewrite the member's review", description = "Needs a won mission with the client; may be held for the moderators")
    @PutMapping
    public NetworkReviewDtos.Company write(@PathVariable Long clientId, @Valid @RequestBody NetworkReviewDtos.Draft draft) {
        return reviews.write(SecurityUtils.getCurrentUserId(), workspace(), clientId, draft);
    }

    @Operation(summary = "Delete the member's review")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long clientId) {
        reviews.delete(SecurityUtils.getCurrentUserId(), workspace(), clientId);
    }

    private Long workspace() {
        return accessGuard.requireWorkspace(null);
    }
}
