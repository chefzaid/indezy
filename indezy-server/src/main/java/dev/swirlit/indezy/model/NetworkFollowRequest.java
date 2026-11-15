package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** A member asking to follow a member whose card is private, until that member approves or declines. */
@Entity
@Table(name = "network_follow_requests",
    indexes = @Index(name = "idx_network_follow_requests_member", columnList = "member_id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_network_follow_requests", columnNames = {"requester_id", "member_id"}))
@Getter
@Setter
public class NetworkFollowRequest extends BaseEntity {

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    /** The member with a private card asked. */
    @Column(name = "member_id", nullable = false)
    private Long memberId;
}
