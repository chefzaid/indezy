package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A post a member saved to their private list, once. */
@Entity
@Table(name = "network_bookmarks",
    indexes = @Index(name = "idx_network_bookmarks_post", columnList = "post_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_bookmarks", columnNames = {"user_id", "post_id"}))
@Getter
@Setter
public class NetworkBookmark extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;
}
