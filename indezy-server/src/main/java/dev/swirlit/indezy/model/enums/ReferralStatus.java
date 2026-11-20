package dev.swirlit.indezy.model.enums;

/** Where a co-optation bonus stands. */
public enum ReferralStatus {
    /** Agreed, not yet due (the mission has not started, or the trial period runs). */
    PROMISED,
    /** To be paid. */
    DUE,
    /** Paid, on a day. */
    PAID
}
