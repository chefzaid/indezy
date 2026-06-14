package dev.swirlit.indezy.model.enums;

/**
 * Why an opportunity was lost/rejected. Used for lost-reason tracking and conversion stats.
 */
public enum LostReason {
    RATE_TOO_LOW,
    POSITION_FILLED,
    NO_RESPONSE,
    NOT_SELECTED,
    WITHDREW,
    OTHER
}
