package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A comment under a post, or a reply to one (one level only). The post's author can hide it: then only they see it,
 * marked hidden.
 */
@Entity
@Table(name = "network_comments", indexes = @Index(name = "idx_network_comments_post", columnList = "post_id"))
@Getter
@Setter
public class NetworkComment extends BaseEntity {

    public static final int TEXT_MAX = 1500;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /** The comment it replies to; none for a comment under the post itself. */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "text", nullable = false, length = TEXT_MAX)
    private String text;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;
}
