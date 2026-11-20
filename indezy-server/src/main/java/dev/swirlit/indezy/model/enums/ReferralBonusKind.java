package dev.swirlit.indezy.model.enums;

/** How a co-optation bonus is counted. */
public enum ReferralBonusKind {
    /** A fixed amount, in euros. */
    FIXED,
    /** An amount in euros per day worked on the mission. */
    PER_DAY,
    /** A percentage of what the mission earns (days worked × daily rate). */
    PERCENT
}
