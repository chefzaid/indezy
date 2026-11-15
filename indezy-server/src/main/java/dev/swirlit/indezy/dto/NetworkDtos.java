package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.NetworkAvailability;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** The member network: an account's membership, its member card, and the requests that join, change and leave. */
public final class NetworkDtos {

    /** Lower-case letters, digits and underscores, 3 to 30, starting with a letter. */
    public static final String HANDLE = "^[a-z][a-z0-9_]{2,29}$";

    private NetworkDtos() {
    }

    /**
     * The account's membership: whether it joined, its handle (a suggested one before joining), the card's fields shown,
     * and the card as members see it (null before joining).
     */
    public record Membership(boolean joined, String handle, Fields fields, Availability availability, MemberCard card) {
    }

    /** How the card tells the availability: from the won missions, or set by hand with the day it names, if any. */
    public record Availability(@NotNull NetworkAvailability mode, LocalDate date) {
    }

    /** What the card shows of the profile, each on or off, and whether the member works remotely. */
    public record Fields(boolean photo, boolean headline, boolean stack, boolean location, boolean availability, boolean languages,
                         boolean remote) {
    }

    /** Joins, or changes the membership: the handle and the card's fields. */
    public record Join(@NotNull @Pattern(regexp = HANDLE) String handle, @NotNull Fields fields, @Valid Availability availability) {
    }

    public record HandleAvailability(String handle, boolean available) {
    }

    /**
     * A post as members read it: its author's card, its text, the #tags it names, when it was posted, whether it is the
     * reader's, how many members like it and whether the reader does, how many comments the reader sees under it and
     * whether its author closed them, whether its author edited it, whether the reader saved it; for a repost, the post it
     * reposts (null when that is gone or out of the reader's reach); how many reposts it has and whether its author turned
     * them off.
     */
    public record Post(Long id, MemberCard author, String text, List<String> tags, LocalDateTime createdAt, boolean own, int likes,
                       boolean liked, int comments, boolean commentsClosed, boolean edited, boolean bookmarked, Original repostOf,
                       int reposts, boolean repostsOff) {

        public Post {
            tags = List.copyOf(tags);
        }

        // Spring MVC logs bodies through toString at DEBUG: not the text a member wrote.
        @Override
        public String toString() {
            return "Post[id=" + id + "]";
        }
    }

    /**
     * A comment as the reader sees it, with its replies when it is under the post itself: hidden ones only the post's
     * author sees, marked so; the pinned one first.
     */
    public record Comment(Long id, MemberCard author, String text, LocalDateTime createdAt, boolean own, boolean hidden, boolean pinned,
                          List<Comment> replies) {

        public Comment {
            replies = List.copyOf(replies);
        }

        @Override
        public String toString() {
            return "Comment[id=" + id + "]";
        }
    }

    /** A new comment, or a reply to the comment named. */
    public record NewComment(@NotBlank @Size(max = 1500) String text, Long parentId) {

        @Override
        public String toString() {
            return "NewComment[length=" + (text == null ? 0 : text.length()) + "]";
        }
    }

    /** The post a repost reposts, as the reader can read it: its author, text and date; unavailable when gone or out of reach. */
    public record Original(Long id, MemberCard author, String text, LocalDateTime createdAt, boolean available) {

        @Override
        public String toString() {
            return "Original[id=" + id + "]";
        }
    }

    /** A repost: an optional comment of up to 3,000 characters. */
    public record NewRepost(@Size(max = 3000) String text) {

        @Override
        public String toString() {
            return "NewRepost[length=" + (text == null ? 0 : text.length()) + "]";
        }
    }

    /** A new post: its text, paragraphs kept. */
    public record NewPost(@NotBlank @Size(max = 3000) String text) {

        @Override
        public String toString() {
            return "NewPost[length=" + (text == null ? 0 : text.length()) + "]";
        }
    }

    /** A tag's page: the tag, whether the reader follows it, a page of the posts naming it, and members whose cards show the skill. */
    public record TagPage(String tag, boolean followed, Posts posts, List<MemberCard> members) {

        public TagPage {
            members = List.copyOf(members);
        }
    }

    /** A page of the reader's saved posts, and the tags of all their saved posts, to filter by. */
    public record SavedPosts(Posts page, List<String> tags) {

        public SavedPosts {
            tags = List.copyOf(tags);
        }
    }

    /** An earlier text of an edited post, and when it was replaced. */
    public record PostVersion(String text, LocalDateTime replacedAt) {

        @Override
        public String toString() {
            return "PostVersion[replacedAt=" + replacedAt + "]";
        }
    }

    /** A page of posts, the newest first, and whether more follow. */
    public record Posts(List<Post> posts, int page, boolean more) {

        public Posts {
            posts = List.copyOf(posts);
        }
    }

    /** Why a member is suggested: members the viewer follows follow them, they share skills, or the same city. */
    public enum SuggestionReason { FOLLOWED_BY, SAME_STACK, SAME_CITY }

    /**
     * A member to follow and why: how many members the viewer follows follow them (and one's name), the skills shared,
     * or the city, the strongest reason first.
     */
    public record Suggestion(MemberCard card, SuggestionReason reason, int followedBy, String followedByName, List<String> sharedSkills) {

        public Suggestion {
            sharedSkills = List.copyOf(sharedSkills);
        }
    }

    /**
     * Notifications grouped as they read: one kind about one post (or one kind of follow) with up to three of the members
     * who did it, how many did, when the latest did, and whether the group was read.
     */
    public record NotificationGroup(NetworkNotificationType type, Long postId, String postExcerpt,
                                    List<MemberCard> actors, int count, LocalDateTime latestAt, boolean read) {

        public NotificationGroup {
            actors = List.copyOf(actors);
        }
    }

    /** The kinds of notification the member turned off. */
    public record NotificationSettings(@NotNull List<NetworkNotificationType> off) {

        public NotificationSettings {
            off = off == null ? null : List.copyOf(off);
        }
    }

    /**
     * What the account did on the network, for its data export: its comments (with the post they answer), the posts it
     * liked and saved, the members it follows (by handle) and the tags it follows.
     */
    public record Activity(List<ExportedComment> comments, List<Long> likedPosts, List<Long> savedPosts, List<String> following,
                           List<String> tags) {

        public Activity {
            comments = List.copyOf(comments);
            likedPosts = List.copyOf(likedPosts);
            savedPosts = List.copyOf(savedPosts);
            following = List.copyOf(following);
            tags = List.copyOf(tags);
        }
    }

    /** A comment the account wrote, for its data export. */
    public record ExportedComment(Long postId, Long replyTo, String text, LocalDateTime createdAt) {

        @Override
        public String toString() {
            return "ExportedComment[postId=" + postId + "]";
        }
    }

    /** A page of the member directory, and how many members match in all. */
    public record Directory(List<MemberCard> members, int total, int page, int size) {

        public Directory {
            members = List.copyOf(members);
        }
    }

    /** The members the viewer mutes and blocks, to undo either. */
    public record Restrictions(List<MemberCard> muted, List<MemberCard> blocked) {

        public Restrictions {
            muted = List.copyOf(muted);
            blocked = List.copyOf(blocked);
        }
    }

    /**
     * A member's page as a member sees it: the card, how many members follow them and they follow, the skills they share
     * with the viewer, whether the viewer follows them and they follow the viewer, whether it is the viewer's own, and
     * whether the viewer mutes or blocks them.
     */
    public record MemberPage(MemberCard card, int followers, int following, List<String> sharedSkills, boolean followed,
                             boolean followsYou, boolean own, boolean muted, boolean blocked) {

        public MemberPage {
            sharedSkills = List.copyOf(sharedSkills);
        }
    }

    /**
     * What members see of a member: the name always, the rest as the member chose; a hidden field is null or empty. The
     * availability is a status (available, on a mission, open to hear of one) with the day it names, if any; verified when
     * the public registry names the member for their billing SIRET.
     */
    public record MemberCard(String handle, String firstName, String lastName, String avatar, String headline, List<String> stack,
                             String city, boolean remote, NetworkAvailability availability, LocalDate availabilityDate,
                             List<String> languages, boolean verified) {

        public MemberCard {
            stack = List.copyOf(stack);
            languages = List.copyOf(languages);
        }

        // Spring MVC logs bodies through toString at DEBUG: nothing about the person (see docs/security.md).
        @Override
        public String toString() {
            return "MemberCard[handle=" + handle + "]";
        }
    }
}
