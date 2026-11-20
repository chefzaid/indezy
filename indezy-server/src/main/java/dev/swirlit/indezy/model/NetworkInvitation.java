package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An invitation to the member network a member emailed to one of their contacts, once per contact: the address kept only
 * as its hash, matched when that person joins.
 */
@Entity
@Table(name = "network_invitations")
@Getter
@Setter
public class NetworkInvitation extends BaseEntity {

    @Column(name = "inviter_id", nullable = false)
    private Long inviterId;

    /** The workspace contact invited; none once that contact is deleted. */
    @Column(name = "contact_id")
    private Long contactId;

    /** The SHA-256 of the address invited, lower-case. */
    @Column(name = "email_hash", nullable = false, length = 64)
    private String emailHash;

    /** The invitation's link, which shows who invites and lets the person decline every invitation. */
    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    /** When the person invited joined the network; none before. */
    @Column(name = "joined_at")
    private LocalDateTime joinedAt;
}
