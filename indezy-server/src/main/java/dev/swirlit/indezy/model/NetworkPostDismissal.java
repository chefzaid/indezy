package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A post a member marked **Not interested** in For you: hidden there, and counting against its author and tags. */
@Entity
@Table(name = "network_post_dismissals",
    uniqueConstraints = @UniqueConstraint(name = "uk_network_post_dismissals", columnNames = {"user_id", "post_id"}))
@Getter
@Setter
public class NetworkPostDismissal extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;
}
