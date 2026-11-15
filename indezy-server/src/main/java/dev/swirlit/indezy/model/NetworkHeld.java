package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A post or a comment the spam score held for review (see docs/security.md#member-network): not published until an
 * administrator lets it go, deleted when they reject it.
 */
@Entity
@Table(name = "network_held", indexes = @Index(name = "idx_network_held_author", columnList = "author_id"))
@Getter
@Setter
public class NetworkHeld extends BaseEntity {

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /** The post a held comment goes under; none for a held post. */
    @Column(name = "post_id")
    private Long postId;

    /** The comment a held reply answers. */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "text", nullable = false, length = NetworkPost.TEXT_MAX)
    private String text;

    /** Why it was held, comma-separated ({@code LINKS,REPEATED}). */
    @Column(name = "reasons", nullable = false, length = 100)
    private String reasons;
}
