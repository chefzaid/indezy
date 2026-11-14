package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.model.enums.NetworkAvailability;
import dev.swirlit.indezy.service.NetworkCommentService;
import dev.swirlit.indezy.service.NetworkDirectoryService;
import dev.swirlit.indezy.service.NetworkFollowService;
import dev.swirlit.indezy.service.NetworkPostService;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    private final NetworkDirectoryService directory;
    private final NetworkPostService posts;
    private final NetworkCommentService comments;

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

    @Operation(summary = "Post to the network", description = "Text of up to 3,000 characters; its #tags are kept")
    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public NetworkDtos.Post post(@Valid @RequestBody NetworkDtos.NewPost request) {
        return posts.post(SecurityUtils.getCurrentUserId(), request);
    }

    @Operation(summary = "Edit one's own post", description = "In the 15 minutes after posting it (409 later); the earlier text is kept")
    @PutMapping("/posts/{id}")
    public NetworkDtos.Post editPost(@PathVariable Long id, @Valid @RequestBody NetworkDtos.NewPost request) {
        return posts.edit(SecurityUtils.getCurrentUserId(), id, request);
    }

    @Operation(summary = "The earlier texts of an edited post, the latest first")
    @GetMapping("/posts/{id}/versions")
    public List<NetworkDtos.PostVersion> postVersions(@PathVariable Long id) {
        return posts.versions(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "Delete one's own post")
    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable Long id) {
        posts.delete(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "Like a post")
    @PutMapping("/posts/{id}/like")
    public NetworkDtos.Post like(@PathVariable Long id) {
        return posts.like(SecurityUtils.getCurrentUserId(), id, true);
    }

    @Operation(summary = "Stop liking a post")
    @DeleteMapping("/posts/{id}/like")
    public NetworkDtos.Post unlike(@PathVariable Long id) {
        return posts.like(SecurityUtils.getCurrentUserId(), id, false);
    }

    @Operation(summary = "The members who like a post, the latest first")
    @GetMapping("/posts/{id}/likes")
    public List<NetworkDtos.MemberCard> likers(@PathVariable Long id) {
        return posts.likers(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "The comments under a post, each with its replies, the pinned one first")
    @GetMapping("/posts/{id}/comments")
    public List<NetworkDtos.Comment> comments(@PathVariable Long id) {
        return comments.list(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "Comment a post, or reply to one of its comments", description = "409 once its author closed the comments")
    @PostMapping("/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public NetworkDtos.Comment comment(@PathVariable Long id, @Valid @RequestBody NetworkDtos.NewComment request) {
        return comments.comment(SecurityUtils.getCurrentUserId(), id, request);
    }

    @Operation(summary = "Delete one's own comment, and its replies")
    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long id) {
        comments.delete(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "Hide a comment under one's own post, or show it again")
    @PutMapping("/comments/{id}/hidden")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void hideComment(@PathVariable Long id, @RequestParam boolean hidden) {
        comments.hide(SecurityUtils.getCurrentUserId(), id, hidden);
    }

    @Operation(summary = "Close the comments of one's own post, or open them again")
    @PutMapping("/posts/{id}/comments-closed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeComments(@PathVariable Long id, @RequestParam boolean closed) {
        comments.close(SecurityUtils.getCurrentUserId(), id, closed);
    }

    @Operation(summary = "Pin a comment of one's own post above the others, or none")
    @PutMapping("/posts/{id}/pinned-comment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pinComment(@PathVariable Long id, @RequestParam(required = false) Long commentId) {
        comments.pin(SecurityUtils.getCurrentUserId(), id, commentId);
    }

    @Operation(summary = "Save a post to one's private list")
    @PutMapping("/posts/{id}/bookmark")
    public NetworkDtos.Post bookmark(@PathVariable Long id) {
        return posts.bookmark(SecurityUtils.getCurrentUserId(), id, true);
    }

    @Operation(summary = "Take a post off one's private list")
    @DeleteMapping("/posts/{id}/bookmark")
    public NetworkDtos.Post unbookmark(@PathVariable Long id) {
        return posts.bookmark(SecurityUtils.getCurrentUserId(), id, false);
    }

    @Operation(summary = "One's saved posts, the latest saved first, 20 a page, those naming a tag when one is given")
    @GetMapping("/bookmarks")
    public NetworkDtos.SavedPosts bookmarks(@RequestParam(required = false) String tag, @RequestParam(defaultValue = "0") int page) {
        return posts.saved(SecurityUtils.getCurrentUserId(), tag, page);
    }

    @Operation(summary = "The home feed: the viewer's posts and those of the members they follow, the newest first, 20 a page")
    @GetMapping("/feed")
    public NetworkDtos.Posts feed(@RequestParam(defaultValue = "0") int page) {
        return posts.feed(SecurityUtils.getCurrentUserId(), page);
    }

    @Operation(summary = "A member's posts, the newest first, 20 a page")
    @GetMapping("/members/{handle}/posts")
    public NetworkDtos.Posts memberPosts(@PathVariable String handle, @RequestParam(defaultValue = "0") int page) {
        return posts.memberPosts(SecurityUtils.getCurrentUserId(), handle, page);
    }

    @Operation(summary = "Stop a member following the viewer")
    @DeleteMapping("/followers/{handle}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFollower(@PathVariable String handle) {
        follows.removeFollower(SecurityUtils.getCurrentUserId(), handle);
    }
}
