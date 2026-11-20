package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkCommunityDtos;
import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.service.NetworkCommunityService;
import dev.swirlit.indezy.util.SecurityUtils;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The member network's communities: created by members, joined at will, moderated by their creator and co-moderators. */
@RestController
@RequestMapping("/network/communities")
@RequiredArgsConstructor
@Tag(name = "Network", description = "The member network of freelancers, joined by choice")
public class NetworkCommunityController {

    private final NetworkCommunityService communities;

    @Operation(summary = "The communities, those the reader is in first, then the largest", description = "q narrows them by name")
    @GetMapping
    public List<NetworkCommunityDtos.Community> list(@RequestParam(defaultValue = "") String q) {
        return communities.list(SecurityUtils.getCurrentUserId(), q);
    }

    @Operation(summary = "Create a community", description = "Its creator moderates it; 10 at most a member")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NetworkCommunityDtos.Community create(@Valid @RequestBody NetworkCommunityDtos.NewCommunity request) {
        return communities.create(SecurityUtils.getCurrentUserId(), request);
    }

    @Operation(summary = "A community's page: the community, its moderators, whether the reader created it")
    @GetMapping("/{slug}")
    public NetworkCommunityDtos.Page page(@PathVariable String slug) {
        return communities.page(SecurityUtils.getCurrentUserId(), slug);
    }

    @Operation(summary = "Change a community's name and description: its moderators")
    @PutMapping("/{slug}")
    public NetworkCommunityDtos.Page update(@PathVariable String slug, @Valid @RequestBody NetworkCommunityDtos.NewCommunity request) {
        return communities.update(SecurityUtils.getCurrentUserId(), slug, request);
    }

    @Operation(summary = "Delete a community: its creator or the administrators", description = "Its posts stay as plain posts")
    @DeleteMapping("/{slug}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String slug) {
        communities.delete(SecurityUtils.getCurrentUserId(), slug);
    }

    @Operation(summary = "Join a community, or ask to join a private one", description = "409 for a member its moderators removed")
    @PutMapping("/{slug}/membership")
    public NetworkCommunityDtos.Page join(@PathVariable String slug) {
        return communities.join(SecurityUtils.getCurrentUserId(), slug);
    }

    @Operation(summary = "Leave a community")
    @DeleteMapping("/{slug}/membership")
    public NetworkCommunityDtos.Page leave(@PathVariable String slug) {
        return communities.leave(SecurityUtils.getCurrentUserId(), slug);
    }

    @Operation(summary = "A community's posts, the newest first, 20 a page")
    @GetMapping("/{slug}/posts")
    public NetworkDtos.Posts posts(@PathVariable String slug, @RequestParam(defaultValue = "0") int page) {
        return communities.posts(SecurityUtils.getCurrentUserId(), slug, page);
    }

    @Operation(summary = "Post in a community, for its members", description = "202 without a post when the spam score holds it")
    @PostMapping("/{slug}/posts")
    public ResponseEntity<NetworkDtos.Post> post(@PathVariable String slug, @Valid @RequestBody NetworkCommunityDtos.NewCommunityPost request) {
        return communities.post(SecurityUtils.getCurrentUserId(), slug, request).map(post -> ResponseEntity.status(HttpStatus.CREATED).body(post))
            .orElseGet(() -> ResponseEntity.accepted().build());
    }

    @Operation(summary = "Remove a post from a community, deleting it: its moderators or the administrators")
    @DeleteMapping("/{slug}/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePost(@PathVariable String slug, @PathVariable Long postId) {
        communities.removePost(SecurityUtils.getCurrentUserId(), slug, postId);
    }

    @Operation(summary = "The members asking to join a private community: its moderators")
    @GetMapping("/{slug}/requests")
    public List<NetworkDtos.MemberCard> requests(@PathVariable String slug) {
        return communities.requests(SecurityUtils.getCurrentUserId(), slug);
    }

    @Operation(summary = "Let a member into a private community, who is told: its moderators")
    @PutMapping("/{slug}/requests/{handle}")
    public List<NetworkDtos.MemberCard> accept(@PathVariable String slug, @PathVariable String handle) {
        return communities.answer(SecurityUtils.getCurrentUserId(), slug, handle, true);
    }

    @Operation(summary = "Decline a request to join a private community, untold: its moderators")
    @DeleteMapping("/{slug}/requests/{handle}")
    public List<NetworkDtos.MemberCard> decline(@PathVariable String slug, @PathVariable String handle) {
        return communities.answer(SecurityUtils.getCurrentUserId(), slug, handle, false);
    }

    @Operation(summary = "A community's members, its moderators first")
    @GetMapping("/{slug}/members")
    public List<NetworkCommunityDtos.CommunityMember> members(@PathVariable String slug) {
        return communities.members(SecurityUtils.getCurrentUserId(), slug);
    }

    @Operation(summary = "Remove a member from a community, who cannot join again", description = "A moderator only by the creator")
    @DeleteMapping("/{slug}/members/{handle}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable String slug, @PathVariable String handle) {
        communities.removeMember(SecurityUtils.getCurrentUserId(), slug, handle);
    }

    @Operation(summary = "Name a member a co-moderator: the creator or the administrators")
    @PutMapping("/{slug}/moderators/{handle}")
    public List<NetworkCommunityDtos.CommunityMember> addModerator(@PathVariable String slug, @PathVariable String handle) {
        return communities.setModerator(SecurityUtils.getCurrentUserId(), slug, handle, true);
    }

    @Operation(summary = "A co-moderator no longer moderates: the creator or the administrators")
    @DeleteMapping("/{slug}/moderators/{handle}")
    public List<NetworkCommunityDtos.CommunityMember> removeModerator(@PathVariable String slug, @PathVariable String handle) {
        return communities.setModerator(SecurityUtils.getCurrentUserId(), slug, handle, false);
    }
}
