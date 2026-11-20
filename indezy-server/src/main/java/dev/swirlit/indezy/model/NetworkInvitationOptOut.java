package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** An address that declined the network's invitations, kept only as its hash: no member invites it again. */
@Entity
@Table(name = "network_invitation_opt_outs")
@Getter
@Setter
public class NetworkInvitationOptOut extends BaseEntity {

    @Column(name = "email_hash", nullable = false, unique = true, length = 64)
    private String emailHash;
}
