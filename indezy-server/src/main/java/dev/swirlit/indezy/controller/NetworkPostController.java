package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.model.enums.NetworkReaction;
import dev.swirlit.indezy.service.NetworkCommentService;
import dev.swirlit.indezy.service.NetworkConfidentialityService;
import dev.swirlit.indezy.service.NetworkDraftService;
import dev.swirlit.indezy.service.NetworkFeedService;
import dev.swirlit.indezy.service.NetworkForYouService;
import dev.swirlit.indezy.service.NetworkPipelineService;
import dev.swirlit.indezy.service.NetworkPollService;
import dev.swirlit.indezy.service.NetworkPostService;
import dev.swirlit.indezy.service.NetworkTagService;
import dev.swirlit.indezy.service.NetworkUsefulService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The member network's posts, comments, drafts and tags. Off until an administrator turns it on. */
@RestController
@RequestMapping("/network")
@RequiredArgsConstructor
@Tag(name = "Network posts", description = "Posts, comments, drafts and tags of the member network")
public class NetworkPostController {

    private static final Map<String, Boolean> HELD = Map.of("held", true);

    private final NetworkPostService posts;
    private final NetworkCommentService comments;
    private final NetworkTagService tags;
    private final NetworkDraftService drafts;
    private final NetworkConfidentialityService confidentiality;
    private final NetworkPollService pollVotes;
    private final NetworkPipelineService pipeline;
    private final NetworkUsefulService usefuls;
    private final NetworkForYouService forYou;
    private final NetworkFeedService feed;

    @Operation(summary = "Post to the network",
        description = "Text of up to 3,000 characters; its #tags are kept. 202 when the spam score holds it for review")
    @PostMapping("/posts")
    public ResponseEntity<Object> post(@Valid @RequestBody NetworkDtos.NewPost request) {
        return createdOrHeld(posts.post(SecurityUtils.getCurrentUserId(), request));
    }

    @Operation(summary = "One post, as its reader may read it", description = "404 when the reader cannot read it")
    @GetMapping("/posts/{id}")
    public NetworkDtos.Post one(@PathVariable Long id) {
        return posts.one(SecurityUtils.getCurrentUserId(), id);
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

    @Operation(summary = "Repost a post, with an optional comment", description = "409 when its author turned reposts off")
    @PostMapping("/posts/{id}/reposts")
    @ResponseStatus(HttpStatus.CREATED)
    public NetworkDtos.Post repost(@PathVariable Long id, @Valid @RequestBody NetworkDtos.NewRepost request) {
        return posts.repost(SecurityUtils.getCurrentUserId(), id, request);
    }

    @Operation(summary = "Turn reposts of one's own post off, or on again")
    @PutMapping("/posts/{id}/reposts-off")
    public NetworkDtos.Post repostsOff(@PathVariable Long id, @RequestParam boolean off) {
        return posts.repostsOff(SecurityUtils.getCurrentUserId(), id, off);
    }

    @Operation(summary = "One's drafts, the scheduled ones first by their time")
    @GetMapping("/drafts")
    public List<NetworkDtos.Draft> drafts() {
        return drafts.list(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Keep a draft, or schedule it from a minute to 30 days ahead")
    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public NetworkDtos.Draft createDraft(@Valid @RequestBody NetworkDtos.DraftRequest request) {
        return drafts.create(SecurityUtils.getCurrentUserId(), request);
    }

    @Operation(summary = "Change a draft or its schedule")
    @PutMapping("/drafts/{id}")
    public NetworkDtos.Draft updateDraft(@PathVariable Long id, @Valid @RequestBody NetworkDtos.DraftRequest request) {
        return drafts.update(SecurityUtils.getCurrentUserId(), id, request);
    }

    @Operation(summary = "Delete a draft")
    @DeleteMapping("/drafts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable Long id) {
        drafts.delete(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "Publish a draft now", description = "202 when the spam score holds it for review")
    @PostMapping("/drafts/{id}/publish")
    public ResponseEntity<Object> publishDraft(@PathVariable Long id) {
        return createdOrHeld(drafts.publish(SecurityUtils.getCurrentUserId(), id));
    }

    @Operation(summary = "Delete one's own post")
    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable Long id) {
        posts.delete(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "React to a post: like it, or another reaction; a member reacts once, the latest reaction kept")
    @PutMapping("/posts/{id}/like")
    public NetworkDtos.Post like(@PathVariable Long id, @RequestParam(defaultValue = "LIKE") NetworkReaction reaction) {
        return posts.like(SecurityUtils.getCurrentUserId(), id, reaction);
    }

    @Operation(summary = "Stop liking a post")
    @DeleteMapping("/posts/{id}/like")
    public NetworkDtos.Post unlike(@PathVariable Long id) {
        return posts.like(SecurityUtils.getCurrentUserId(), id, null);
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

    @Operation(summary = "Comment a post, or reply to one of its comments",
        description = "409 once its author closed the comments; 202 when the spam score holds it for review")
    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<Object> comment(@PathVariable Long id, @Valid @RequestBody NetworkDtos.NewComment request) {
        return createdOrHeld(comments.comment(SecurityUtils.getCurrentUserId(), id, request));
    }

    @Operation(summary = "Like a comment the reader sees", description = "Once; its writer is told")
    @PutMapping("/comments/{id}/like")
    public NetworkDtos.Comment likeComment(@PathVariable Long id) {
        return comments.like(SecurityUtils.getCurrentUserId(), id, true);
    }

    @Operation(summary = "No longer like a comment")
    @DeleteMapping("/comments/{id}/like")
    public NetworkDtos.Comment unlikeComment(@PathVariable Long id) {
        return comments.like(SecurityUtils.getCurrentUserId(), id, false);
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

    @Operation(summary = "Add a mission passed on to one's pipeline", description = "Once; named lets its author see who took it")
    @PostMapping("/posts/{id}/pipeline")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto take(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean named) {
        return pipeline.take(SecurityUtils.getCurrentUserId(), id, named);
    }

    @Operation(summary = "The members who took one's own mission and let them know")
    @GetMapping("/posts/{id}/takers")
    public List<NetworkDtos.MemberCard> takers(@PathVariable Long id) {
        return pipeline.takers(SecurityUtils.getCurrentUserId(), id);
    }

    @Operation(summary = "Find a post useful", description = "The second reaction, once; never on one's own post")
    @PutMapping("/posts/{id}/useful")
    public NetworkDtos.Post useful(@PathVariable Long id) {
        return usefuls.useful(SecurityUtils.getCurrentUserId(), id, true);
    }

    @Operation(summary = "No longer find a post useful")
    @DeleteMapping("/posts/{id}/useful")
    public NetworkDtos.Post notUseful(@PathVariable Long id) {
        return usefuls.useful(SecurityUtils.getCurrentUserId(), id, false);
    }

    @Operation(summary = "Vote in a poll, or change one's vote", description = "While it is open; the choice counts from 0")
    @PutMapping("/posts/{id}/vote")
    public NetworkDtos.Post vote(@PathVariable Long id, @RequestParam int choice) {
        return pollVotes.vote(SecurityUtils.getCurrentUserId(), id, choice);
    }

    @Operation(summary = "Pick a comment under one's own question as its answer", description = "None (no commentId) to clear it")
    @PutMapping("/posts/{id}/answer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void answer(@PathVariable Long id, @RequestParam(required = false) Long commentId) {
        comments.answer(SecurityUtils.getCurrentUserId(), id, commentId);
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

    @Operation(summary = "A tag's page: whether the viewer follows it, its posts (20 a page) and members whose cards show the skill")
    @GetMapping("/tags/{tag}")
    public NetworkDtos.TagPage tag(@PathVariable String tag, @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "recent") String sort) {
        return tags.page(SecurityUtils.getCurrentUserId(), tag, page, "useful".equals(sort));
    }

    @Operation(summary = "The tags the viewer follows")
    @GetMapping("/tags")
    public List<String> followedTags() {
        return tags.followed(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Follow a tag: posts naming it reach the home feed")
    @PutMapping("/tags/{tag}/follow")
    public List<String> followTag(@PathVariable String tag) {
        return tags.follow(SecurityUtils.getCurrentUserId(), tag, true);
    }

    @Operation(summary = "Stop following a tag")
    @DeleteMapping("/tags/{tag}/follow")
    public List<String> unfollowTag(@PathVariable String tag) {
        return tags.follow(SecurityUtils.getCurrentUserId(), tag, false);
    }

    @Operation(summary = "For you: the recent posts of every member the reader can read, ranked by what they share with the reader")
    @GetMapping("/feed/for-you")
    public NetworkDtos.Posts forYou(@RequestParam(defaultValue = "0") int page) {
        return forYou.forYou(SecurityUtils.getCurrentUserId(), page);
    }

    @Operation(summary = "The home feed: the viewer's posts, those of the members and tags they follow, the newest first, 20 a page")
    @GetMapping("/feed")
    public NetworkDtos.Posts feed(@RequestParam(defaultValue = "0") int page) {
        return feed.feed(SecurityUtils.getCurrentUserId(), page);
    }

    @Operation(summary = "How many posts by others reached the home feed after the given time")
    @GetMapping("/feed/newer")
    public NetworkDtos.Newer newer(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        return feed.newer(SecurityUtils.getCurrentUserId(), since);
    }

    @Operation(summary = "The tags most named in the last seven days by the posts the reader can read, 8 at most")
    @GetMapping("/tags/trending")
    public List<NetworkDtos.TrendingTag> trending() {
        return feed.trending(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "The tags in use starting with what was typed, the most used first, 5 at most")
    @GetMapping("/tags/suggest")
    public List<String> suggestTags(@RequestParam(defaultValue = "") String q) {
        return feed.suggestTags(SecurityUtils.getCurrentUserId(), q);
    }

    @Operation(summary = "A member's posts, the newest first, 20 a page")
    @GetMapping("/members/{handle}/posts")
    public NetworkDtos.Posts memberPosts(@PathVariable String handle, @RequestParam(defaultValue = "0") int page) {
        return posts.memberPosts(SecurityUtils.getCurrentUserId(), handle, page);
    }

    @Operation(summary = "Find the workspace's client, ESN and contact names in a text before it goes out",
        description = "Reads only; the member decides to anonymize or to publish anyway")
    @PostMapping("/confidentiality")
    public List<NetworkDtos.ConfidentialMatch> confidentiality(@Valid @RequestBody NetworkDtos.ConfidentialityCheck request) {
        return confidentiality.check(SecurityUtils.getCurrentUserId(), request.text());
    }

    /** 201 with what was published, or 202 when the spam score holds it for an administrator. */
    private static ResponseEntity<Object> createdOrHeld(Optional<?> published) {
        return published.<ResponseEntity<Object>>map(body -> ResponseEntity.status(HttpStatus.CREATED).body(body))
            .orElseGet(() -> ResponseEntity.accepted().body(HELD));
    }
}
