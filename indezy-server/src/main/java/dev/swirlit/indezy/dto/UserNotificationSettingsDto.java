package dev.swirlit.indezy.dto;

import lombok.Data;

/** Which notifications the account gets, by event and channel (see docs/features.md#notifications). */
@Data
public class UserNotificationSettingsDto {
    /** The weekly digest, by email on Mondays. */
    private Boolean weeklyReports;
    /** The count of the saved searches' new offers next to Offers in the menu. */
    private Boolean newOffersBadge;
    /** Whether this deployment can send emails at all; read-only. */
    private boolean emailsAvailable;
}
