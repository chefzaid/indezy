package dev.swirlit.indezy.model.enums;

/** A section of the weekly digest, which the account can leave out (see docs/features.md#weekly-digest). */
public enum DigestSection {
    PIPELINE,
    OVERDUE_INVOICES,
    INTERVIEWS,
    FOLLOW_UPS,
    QUIET_OPPORTUNITIES,
    MISSIONS_ENDING,
    OFFERS,
    /** The week's most liked post of each tag followed in the member network. */
    NETWORK_TAGS
}
