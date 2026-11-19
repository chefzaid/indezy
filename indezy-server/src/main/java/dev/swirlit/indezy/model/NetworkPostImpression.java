package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** How often a member was shown a post (in a feed, a page or the search), for its author's analytics; one row per pair. */
@Entity
@Table(name = "network_post_impressions",
    uniqueConstraints = @UniqueConstraint(name = "uk_network_post_impressions", columnNames = {"post_id", "viewer_id"}))
@Getter
@Setter
public class NetworkPostImpression extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "viewer_id", nullable = false)
    private Long viewerId;

    /** How many times, a page's reading counting once. */
    @Column(name = "times", nullable = false)
    private int times;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}
