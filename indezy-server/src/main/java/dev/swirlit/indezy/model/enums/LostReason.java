package dev.swirlit.indezy.model.enums;

/**
 * Reason an opportunity was lost or rejected, used for funnel analysis.
 */
public enum LostReason {
    RATE_TOO_LOW,
    POSITION_FILLED,
    NO_RESPONSE,
    PROFILE_MISMATCH,
    CLIENT_CANCELED,
    ACCEPTED_OTHER_OFFER,
    OTHER
}
