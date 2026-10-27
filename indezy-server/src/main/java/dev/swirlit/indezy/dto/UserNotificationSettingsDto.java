package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.DigestSection;
import lombok.Data;

import java.util.Set;

/** Which notifications the account gets, by event and channel (see docs/features.md#notifications). */
@Data
public class UserNotificationSettingsDto {
    /** The weekly digest, by email on Mondays. */
    private Boolean weeklyReports;
    /** The digest's sections kept; the others are left out. */
    private Set<DigestSection> weeklyDigestSections;
    /** An email for each call a recruiter books through the booking link. */
    private Boolean bookingEmails;
    /** The count of the saved searches' new offers next to Offers in the menu. */
    private Boolean newOffersBadge;
    /** Whether this deployment can send emails at all; read-only. */
    private boolean emailsAvailable;
}
