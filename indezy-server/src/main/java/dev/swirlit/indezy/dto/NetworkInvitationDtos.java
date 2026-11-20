package dev.swirlit.indezy.dto;

import java.time.LocalDateTime;

/** Invitations to the member network, as the API reads them. */
public final class NetworkInvitationDtos {

    private NetworkInvitationDtos() {
    }

    /** Where a contact stands toward the network. */
    public enum Standing {
        /** The contact has no email address. */
        NO_EMAIL,
        /** Never invited by the reader, and invitable. */
        INVITABLE,
        /** Invited by the reader, not yet a member. */
        INVITED,
        /** Already a member: their page's handle given. */
        MEMBER,
        /** Declined every invitation to the network. */
        DECLINED
    }

    /** A contact's standing, when the reader invited them, and their handle once a member. */
    public record ContactStanding(Standing standing, LocalDateTime invitedAt, String handle) {
    }

    /** An invitation as the person invited reads it from its link: who invites, and whether they declined. */
    public record PublicInvitation(String inviter, boolean declined) {
    }
}
