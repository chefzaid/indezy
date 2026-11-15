package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member liking a comment, once; the post is kept so its comments' likes go with it. */
@Entity
@Table(name = "network_comment_likes",
    indexes = @Index(name = "idx_network_comment_likes_post", columnList = "post_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_comment_likes", columnNames = {"comment_id", "user_id"}))
@Getter
@Setter
public class NetworkCommentLike extends BaseEntity {

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
