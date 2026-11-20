package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member in a community: a moderator or not; a member its moderators removed stays listed, kept from joining again. */
@Entity
@Table(name = "network_community_members",
    uniqueConstraints = @UniqueConstraint(name = "uk_network_community_members", columnNames = {"community_id", "user_id"}))
@Getter
@Setter
public class NetworkCommunityMember extends BaseEntity {

    @Column(name = "community_id", nullable = false)
    private Long communityId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "moderator", nullable = false)
    private boolean moderator;

    /** Removed by its moderators: no longer in it, and cannot join again. */
    @Column(name = "removed", nullable = false)
    private boolean removed;
}
