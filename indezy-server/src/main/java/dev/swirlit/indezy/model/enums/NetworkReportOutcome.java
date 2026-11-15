package dev.swirlit.indezy.model.enums;

/** What the moderators decided on a report. */
public enum NetworkReportOutcome {
    /** Nothing against the rules. */
    DISMISSED,
    /** The author is warned. */
    WARNED,
    /** The post or the comment is removed, and its author told. */
    REMOVED,
    /** The author cannot post, comment, repost, follow or report for some days. */
    SUSPENDED,
    /** The author's membership is ended, for good. */
    BANNED
}
