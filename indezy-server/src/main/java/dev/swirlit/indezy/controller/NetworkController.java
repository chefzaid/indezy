package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.dto.TestimonialDtos;
import dev.swirlit.indezy.model.enums.NetworkAvailability;
import dev.swirlit.indezy.service.NetworkDirectoryService;
import dev.swirlit.indezy.service.NetworkFollowRequestService;
import dev.swirlit.indezy.service.NetworkFollowService;
import dev.swirlit.indezy.service.NetworkNotificationService;
import dev.swirlit.indezy.service.NetworkPageViewService;
import dev.swirlit.indezy.service.NetworkRecommendationService;
import dev.swirlit.indezy.service.NetworkService;
import dev.swirlit.indezy.service.NetworkSuggestionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The member network: joining, the member card, and the handles. Off until an administrator turns it on. */
@RestController
@RequestMapping("/network")
@RequiredArgsConstructor
@Tag(name = "Network", description = "The member network of freelancers, joined by choice")
public class NetworkController {

    private final NetworkService network;
    private final NetworkFollowService follows;
    private final NetworkFollowRequestService followRequests;
    private final NetworkDirectoryService directory;
    private final NetworkSuggestionService suggestions;
    private final NetworkNotificationService notices;
    private final NetworkRecommendationService recommendations;
    private final NetworkPageViewService pageViews;

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

    @Operation(summary = "The member directory: members found by a skill, a city, an availability or remote work",
        description = "On what each card shows only; 20 a page, by last name")
    @GetMapping("/members")
    public NetworkDtos.Directory directory(@RequestParam(required = false) String skill, @RequestParam(required = false) String city,
                                           @RequestParam(required = false) NetworkAvailability availability,
                                           @RequestParam(required = false) Boolean remote, @RequestParam(defaultValue = "0") int page) {
        return directory.search(SecurityUtils.getCurrentUserId(), new NetworkDirectoryService.Search(skill, city, availability, remote, page));
    }

    @Operation(summary = "Members to mention: handle, first or last name starting with what was typed, those followed first, 5 at most")
    @GetMapping("/mentions")
    public List<NetworkDtos.MemberCard> mentions(@RequestParam(defaultValue = "") String q) {
        return directory.mentions(SecurityUtils.getCurrentUserId(), q);
    }

    @Operation(summary = "The members the viewer follows who follow the member too", description = "How many, and the first three")
    @GetMapping("/members/{handle}/mutual")
    public NetworkDtos.MutualFollows mutual(@PathVariable String handle) {
        return follows.mutual(SecurityUtils.getCurrentUserId(), handle);
    }

    @Operation(summary = "The viewer opened a member's page", description = "Kept once a day per pair for 90 days; one's own page counts nothing")
    @PostMapping("/members/{handle}/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void viewed(@PathVariable String handle) {
        pageViews.record(SecurityUtils.getCurrentUserId(), handle);
    }

    @Operation(summary = "Who viewed the member's page in the last 90 days",
        description = "Those browsing privately are counted without a name; a member browsing privately sees no names")
    @GetMapping("/page-views")
    public NetworkDtos.PageViews pageViews() {
        return pageViews.viewers(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Browse other members' pages privately, or no longer")
    @PutMapping("/page-views/private")
    public NetworkDtos.PageViews privateViewing(@RequestParam boolean on) {
        return pageViews.setPrivateViewing(SecurityUtils.getCurrentUserId(), on);
    }

    @Operation(summary = "A member's recommendations: the testimonials they approved for their public page, the latest first",
        description = "None for a private card the viewer does not follow; 404 as for the member's card")
    @GetMapping("/members/{handle}/recommendations")
    public List<TestimonialDtos.Shown> recommendations(@PathVariable String handle) {
        return recommendations.recommendations(SecurityUtils.getCurrentUserId(), handle);
    }

    @Operation(summary = "The network's search for members",
        description = "Every word typed names the member by the start of their handle, first or last name, a skill or their city, or within " +
            "their headline, on what their cards show; by name first, then those followed; 20 a page")
    @GetMapping("/search/members")
    public NetworkDtos.Directory searchMembers(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page) {
        return directory.find(SecurityUtils.getCurrentUserId(), q, page);
    }

    @Operation(summary = "Members to follow, each with its reason: followed by members the viewer follows, same skills or city")
    @GetMapping("/suggestions")
    public List<NetworkDtos.Suggestion> suggestions() {
        return suggestions.suggestions(SecurityUtils.getCurrentUserId());
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

    @Operation(summary = "The viewer's notifications, grouped, the newest first")
    @GetMapping("/notifications")
    public List<NetworkDtos.NotificationGroup> notifications() {
        return notices.list(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "How many of the viewer's notifications are unread")
    @GetMapping("/notifications/unread")
    public Map<String, Long> unreadNotifications() {
        return Map.of("unread", notices.unread(SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "Mark the viewer's notifications read")
    @PostMapping("/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readNotifications() {
        notices.markRead(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "The kinds of notification the viewer turned off")
    @GetMapping("/notifications/settings")
    public NetworkDtos.NotificationSettings notificationSettings() {
        return notices.settings(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Turn kinds of notification off, and the others on")
    @PutMapping("/notifications/settings")
    public NetworkDtos.NotificationSettings saveNotificationSettings(@Valid @RequestBody NetworkDtos.NotificationSettings settings) {
        return notices.saveSettings(SecurityUtils.getCurrentUserId(), settings);
    }

    @Operation(summary = "Stop a member following the viewer")
    @DeleteMapping("/followers/{handle}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFollower(@PathVariable String handle) {
        follows.removeFollower(SecurityUtils.getCurrentUserId(), handle);
    }

    @Operation(summary = "Suggest members who went through the same ESNs, or no longer", description = "Only between members who both turn it on")
    @PutMapping("/membership/esn-suggestions")
    public NetworkDtos.Membership setEsnSuggestions(@RequestParam boolean on) {
        return network.setEsnSuggestions(SecurityUtils.getCurrentUserId(), on);
    }

    @Operation(summary = "Turn the member's card private, or public again",
        description = "Private: following asks the member, and only their followers read their posts. Public again approves every request")
    @PutMapping("/membership/private")
    public NetworkDtos.Membership setPrivate(@RequestParam("private") boolean privateCard) {
        return followRequests.setPrivate(SecurityUtils.getCurrentUserId(), privateCard);
    }

    @Operation(summary = "The members asking to follow the member, the oldest first")
    @GetMapping("/follow-requests")
    public List<NetworkDtos.MemberCard> followRequests() {
        return followRequests.requests(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "The members the member asked to follow, waiting for their answer, the latest asked first")
    @GetMapping("/follow-requests/sent")
    public List<NetworkDtos.MemberCard> sentFollowRequests() {
        return followRequests.sent(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Approve a member's request to follow", description = "They follow, and are told")
    @PutMapping("/follow-requests/{handle}")
    public List<NetworkDtos.MemberCard> approveFollowRequest(@PathVariable String handle) {
        return followRequests.answer(SecurityUtils.getCurrentUserId(), handle, true);
    }

    @Operation(summary = "Decline a member's request to follow", description = "They are not told")
    @DeleteMapping("/follow-requests/{handle}")
    public List<NetworkDtos.MemberCard> declineFollowRequest(@PathVariable String handle) {
        return followRequests.answer(SecurityUtils.getCurrentUserId(), handle, false);
    }
}
