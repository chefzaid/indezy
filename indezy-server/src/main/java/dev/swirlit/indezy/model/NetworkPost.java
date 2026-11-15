package dev.swirlit.indezy.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import dev.swirlit.indezy.model.enums.NetworkPostKind;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** A member's post to the network: its text and the #tags it names, kept lower-case. */
@Entity
@Table(name = "network_posts", indexes = @Index(name = "idx_network_posts_author", columnList = "author_id"))
@Getter
@Setter
public class NetworkPost extends BaseEntity {

    public static final int TEXT_MAX = 3000;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "text", nullable = false, length = TEXT_MAX)
    private String text;

    /** When its author last changed its text, within the edit window; none while never edited. */
    @Column(name = "edited_at")
    private Instant editedAt;

    /** The post it reposts; its own text, which may be empty, comments it. */
    @Column(name = "repost_of_id")
    private Long repostOfId;

    /** Its author turned reposts off: none can be made of it. */
    @Column(name = "reposts_off", nullable = false)
    private boolean repostsOff;

    /** No new comment once its author closes them. */
    @Column(name = "comments_closed", nullable = false)
    private boolean commentsClosed;

    /** The comment its author pinned above the others. */
    @Column(name = "pinned_comment_id")
    private Long pinnedCommentId;

    /** What the post is; none (null) reads as a plain post. */
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20)
    private NetworkPostKind kind;

    /** The facts of a mission or an availability. */
    @Embedded
    private NetworkPostDetails details;

    /** A poll's choices, one a line. */
    @Column(name = "poll_choices", length = 400)
    private String pollChoices;

    /** When a poll closes. */
    @Column(name = "poll_ends_at")
    private Instant pollEndsAt;

    /** The reply a question's author picked as its answer. */
    @Column(name = "answer_comment_id")
    private Long answerCommentId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "network_post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tag", length = 40)
    private Set<String> tags = new LinkedHashSet<>();

    public NetworkPostKind getKind() {
        return kind == null ? NetworkPostKind.POST : kind;
    }

    public Set<String> getTags() {
        return tags != null ? new LinkedHashSet<>(tags) : new LinkedHashSet<>();
    }

    public void setTags(final Set<String> tags) {
        this.tags = tags != null ? new LinkedHashSet<>(tags) : new LinkedHashSet<>();
    }
}
