package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.NetworkPostDetails;
import dev.swirlit.indezy.model.enums.NetworkAvailability;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.model.enums.NetworkPostKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
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
    public record Membership(boolean joined, String handle, Fields fields, Availability availability, MemberCard card,
                             boolean privateCard) {
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
                       int reposts, boolean repostsOff, NetworkPostKind kind, PostDetails details, Long answerCommentId, Poll poll,
                       Taken taken, Useful useful) {

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
                          int likes, boolean liked, List<Comment> replies) {

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
    public record NewPost(@NotBlank @Size(max = 3000) String text, NetworkPostKind kind, @Valid PostDetails details, @Valid NewPoll poll) {

        public NewPost(String text) {
            this(text, null, null, null);
        }

        @Override
        public String toString() {
            return "NewPost[length=" + (text == null ? 0 : text.length()) + ", kind=" + kind + "]";
        }
    }

    /** A poll's two to four choices and how many days it stays open. */
    public record NewPoll(@NotNull @Size(min = 2, max = 4) List<@NotBlank @Size(max = 80) String> choices, @NotNull @Min(1) @Max(7) Integer days) {

        public NewPoll {
            choices = choices == null ? null : List.copyOf(choices);
        }
    }

    /**
     * A poll as its reader sees it: each choice with its votes once the reader voted, the poll closed or it is theirs (none
     * before), when it closes, and the reader's own choice.
     */
    public record Poll(List<PollChoice> choices, Instant endsAt, boolean closed, Integer myChoice, Integer total) {

        public Poll {
            choices = List.copyOf(choices);
        }
    }

    public record PollChoice(String text, Integer votes) {
    }

    /** How many members found the post useful, and whether the reader did. */
    public record Useful(int count, boolean mine) {
    }

    /** A mission passed on: how many members took it into their pipeline (to its author only), and whether the reader did. */
    public record Taken(Integer count, boolean mine) {
    }

    /** The facts of a mission to pass on or of an availability: the role, the daily rate range, where, when, the client if named. */
    public record PostDetails(@Size(max = NetworkPostDetails.FIELD_MAX) String role, @Min(0) @Max(RATE_MAX) Integer rateMin,
                              @Min(0) @Max(RATE_MAX) Integer rateMax, @Size(max = NetworkPostDetails.FIELD_MAX) String city,
                              @Min(0) @Max(5) Integer remoteDays, LocalDate start, @Size(max = NetworkPostDetails.FIELD_MAX) String client,
                              @Size(max = 10) List<@NotBlank @Size(max = 40) String> stack) {

        public static final int RATE_MAX = 5000;

        public PostDetails {
            stack = stack == null ? List.of() : List.copyOf(stack);
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

    /** A draft of the member's: its text, and when it is published on its own, if it is scheduled. */
    public record Draft(Long id, String text, Instant scheduledAt, LocalDateTime updatedAt) {

        @Override
        public String toString() {
            return "Draft[id=" + id + "]";
        }
    }

    /** A draft to keep: its text, and when to publish it on its own, from a minute to 30 days ahead; none to keep it. */
    public record DraftRequest(@NotBlank @Size(max = 3000) String text, Instant scheduledAt) {

        @Override
        public String toString() {
            return "DraftRequest[length=" + (text == null ? 0 : text.length()) + "]";
        }
    }

    /** A page of posts, the newest first, and whether more follow. */
    public record Posts(List<Post> posts, int page, boolean more) {

        public Posts {
            posts = List.copyOf(posts);
        }
    }

    /**
     * Why a member is suggested: one of the viewer's Indezy contacts, followed by members the viewer follows, sharing
     * skills, or in the same city.
     */
    public enum SuggestionReason { CONTACT, FOLLOWED_BY, SAME_STACK, SAME_CITY }

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
    public record Activity(List<ExportedComment> comments, List<Long> likedPosts, List<Long> likedComments, List<Long> savedPosts,
                           List<String> following, List<String> tags) {

        public Activity {
            comments = List.copyOf(comments);
            likedPosts = List.copyOf(likedPosts);
            likedComments = List.copyOf(likedComments);
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
                             boolean followsYou, boolean own, boolean muted, boolean blocked, boolean privateCard, boolean requested) {

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

    /** A followed tag's most liked post of the week, as the weekly digest tells it: the author's name and its first words. */
    public record DigestPost(String tag, String author, String excerpt, long likes) {

        @Override
        public String toString() {
            return "DigestPost[tag=" + tag + ", likes=" + likes + "]";
        }
    }

    /** What a confidential name in a text is. */
    public enum ConfidentialKind {
        CLIENT,
        ESN,
        CONTACT
    }

    /** The text to check before it goes out. */
    public record ConfidentialityCheck(@NotNull @Size(max = NetworkPost.TEXT_MAX) String text) {

        @Override
        public String toString() {
            return "ConfidentialityCheck[length=" + (text == null ? 0 : text.length()) + "]";
        }
    }

    /** A name of the workspace found in the text, as written there, with its kind and, for a client, its city. */
    public record ConfidentialMatch(String found, ConfidentialKind kind, String city) {

        @Override
        public String toString() {
            return "ConfidentialMatch[kind=" + kind + "]";
        }
    }
}
