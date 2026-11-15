package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.admin.AdminService;
import dev.swirlit.indezy.dto.NetworkModerationDtos;
import dev.swirlit.indezy.service.NetworkModerationService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Members report posts, comments and members; administrators decide in the moderation queue. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Network moderation", description = "Reports of the member network and the administrators' moderation queue")
public class NetworkModerationController {

    private final NetworkModerationService moderation;
    private final AdminService admin;

    @Operation(summary = "Report a post to the moderators", description = "Once while the report is open")
    @PostMapping("/network/posts/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportPost(@PathVariable Long id, @Valid @RequestBody NetworkModerationDtos.NewReport request) {
        moderation.reportPost(SecurityUtils.getCurrentUserId(), id, request);
    }

    @Operation(summary = "Report a comment to the moderators", description = "Once while the report is open")
    @PostMapping("/network/comments/{id}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportComment(@PathVariable Long id, @Valid @RequestBody NetworkModerationDtos.NewReport request) {
        moderation.reportComment(SecurityUtils.getCurrentUserId(), id, request);
    }

    @Operation(summary = "Report a member to the moderators", description = "Once while the report is open")
    @PostMapping("/network/members/{handle}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportMember(@PathVariable String handle, @Valid @RequestBody NetworkModerationDtos.NewReport request) {
        moderation.reportMember(SecurityUtils.getCurrentUserId(), handle, request);
    }

    @Operation(summary = "The moderation queue: the open reports, the oldest first, and the latest decisions")
    @GetMapping("/admin/network/reports")
    public NetworkModerationDtos.Queue queue() {
        admin.requireAdmin(SecurityUtils.getCurrentUserId());
        return moderation.queue();
    }

    @Operation(summary = "Decide a report", description = "The decision falls on every open report of the same target")
    @PutMapping("/admin/network/reports/{id}")
    public NetworkModerationDtos.Queue decide(@PathVariable Long id, @Valid @RequestBody NetworkModerationDtos.Decision decision) {
        Long adminId = SecurityUtils.getCurrentUserId();
        admin.requireAdmin(adminId);
        return moderation.decide(adminId, id, decision);
    }
}
