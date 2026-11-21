package dev.swirlit.indezy.model.enums;

/** Where a review of a client or an ESN stands. */
public enum NetworkReviewStatus {
    /** Shown to the network's members. */
    PUBLISHED,
    /** Held by the filter until an administrator publishes or rejects it. */
    HELD,
    /** Rejected by an administrator while held. */
    REJECTED,
    /** Removed by an administrator after a report. */
    REMOVED
}
