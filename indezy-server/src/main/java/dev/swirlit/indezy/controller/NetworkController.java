package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.service.NetworkFollowService;
import dev.swirlit.indezy.service.NetworkService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The member network: joining, the member card, and the handles. Off until an administrator turns it on. */
@RestController
@RequestMapping("/network")
@RequiredArgsConstructor
@Tag(name = "Network", description = "The member network of freelancers, joined by choice")
public class NetworkController {

    private final NetworkService network;
    private final NetworkFollowService follows;

    @Operation(summary = "Whether the account joined, its handle and what its card shows")
    @GetMapping("/membership")
    public NetworkDtos.Membership membership() {
        return network.membership(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Join the network, or change the handle and the card", description = "409 for a handle another member holds")
    @PutMapping("/membership")
    public NetworkDtos.Membership join(@Valid @RequestBody NetworkDtos.Join request) {
        return network.join(SecurityUtils.getCurrentUserId(), request);
    }

    @Operation(summary = "Leave the network: the card is hidden at once")
    @DeleteMapping("/membership")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave() {
        network.leave(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Verify the member against the public company registry, from the billing SIRET",
        description = "409 without a SIRET, for an establishment the registry does not have active or that names someone else; 503 when it cannot be asked")
    @PostMapping("/membership/verification")
    public NetworkDtos.Membership verify() {
        return network.verify(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Whether a handle can be taken")
    @GetMapping("/handles/{handle}")
    public NetworkDtos.HandleAvailability handle(@PathVariable String handle) {
        return network.availability(SecurityUtils.getCurrentUserId(), handle);
    }

    @Operation(summary = "A member's card, for members", description = "409 until the account joins, 404 for a handle no member holds")
    @GetMapping("/members/{handle}")
    public NetworkDtos.MemberCard card(@PathVariable String handle) {
        return network.card(SecurityUtils.getCurrentUserId(), handle);
    }

    @Operation(summary = "A member's page: the card, the counts, the skills shared with the viewer and who follows whom")
    @GetMapping("/members/{handle}/page")
    public NetworkDtos.MemberPage page(@PathVariable String handle) {
        return follows.page(SecurityUtils.getCurrentUserId(), handle);
    }

    @Operation(summary = "Follow a member", description = "400 for the viewer's own handle")
    @PutMapping("/members/{handle}/follow")
    public NetworkDtos.MemberPage follow(@PathVariable String handle) {
        return follows.follow(SecurityUtils.getCurrentUserId(), handle);
    }

    @Operation(summary = "Stop following a member")
    @DeleteMapping("/members/{handle}/follow")
    public NetworkDtos.MemberPage unfollow(@PathVariable String handle) {
        return follows.unfollow(SecurityUtils.getCurrentUserId(), handle);
    }

    @Operation(summary = "The members, still in the network, who follow a member")
    @GetMapping("/members/{handle}/followers")
    public List<NetworkDtos.MemberCard> followers(@PathVariable String handle) {
        return follows.followers(SecurityUtils.getCurrentUserId(), handle);
    }

    @Operation(summary = "The members, still in the network, a member follows")
    @GetMapping("/members/{handle}/following")
    public List<NetworkDtos.MemberCard> following(@PathVariable String handle) {
        return follows.following(SecurityUtils.getCurrentUserId(), handle);
    }

    @Operation(summary = "Mute a member: their posts are hidden, the follow stays")
    @PutMapping("/members/{handle}/mute")
    public NetworkDtos.MemberPage mute(@PathVariable String handle) {
        return follows.mute(SecurityUtils.getCurrentUserId(), handle, true);
    }

    @Operation(summary = "Stop muting a member")
    @DeleteMapping("/members/{handle}/mute")
    public NetworkDtos.MemberPage unmute(@PathVariable String handle) {
        return follows.mute(SecurityUtils.getCurrentUserId(), handle, false);
    }

    @Operation(summary = "Block a member: no follow either way, and the viewer's page hidden from them")
    @PutMapping("/members/{handle}/block")
    public NetworkDtos.MemberPage block(@PathVariable String handle) {
        return follows.block(SecurityUtils.getCurrentUserId(), handle, true);
    }

    @Operation(summary = "Stop blocking a member")
    @DeleteMapping("/members/{handle}/block")
    public NetworkDtos.MemberPage unblock(@PathVariable String handle) {
        return follows.block(SecurityUtils.getCurrentUserId(), handle, false);
    }

    @Operation(summary = "The members the viewer mutes and blocks")
    @GetMapping("/restrictions")
    public NetworkDtos.Restrictions restrictions() {
        return follows.restrictions(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Stop a member following the viewer")
    @DeleteMapping("/followers/{handle}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFollower(@PathVariable String handle) {
        follows.removeFollower(SecurityUtils.getCurrentUserId(), handle);
    }
}
