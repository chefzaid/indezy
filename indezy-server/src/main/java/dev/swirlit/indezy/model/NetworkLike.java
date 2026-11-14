package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member liking a post, once. */
@Entity
@Table(name = "network_likes",
    indexes = @Index(name = "idx_network_likes_post", columnList = "post_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_likes", columnNames = {"post_id", "user_id"}))
@Getter
@Setter
public class NetworkLike extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
