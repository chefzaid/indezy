package dev.swirlit.indezy.model.enums;

/** What a network notification tells its recipient. */
public enum NetworkNotificationType {
    /** A member followed them. */
    FOLLOW,
    /** A member asked to follow them, their card being private. */
    FOLLOW_REQUEST,
    /** A member approved their request to follow. */
    FOLLOW_ACCEPTED,
    /** A member liked their post. */
    LIKE,
    /** A member liked their comment. */
    COMMENT_LIKE,
    /** A member commented their post. */
    COMMENT,
    /** A member replied to their comment. */
    REPLY,
    /** A member mentioned them in a post or a comment. */
    MENTION,
    /** A member reposted their post. */
    REPOST,
    /** The moderators warned them about a post, a comment or their conduct. */
    WARNED,
    /** The moderators removed their post or comment. */
    CONTENT_REMOVED,
    /** The moderators suspended them for some days. */
    SUSPENDED,
    /** The moderators acted on a report they made. */
    REPORT_ACTED,
    /** The moderators found nothing against the rules in a report they made. */
    REPORT_DISMISSED,
    /** A member posted in a community the recipient is in. */
    COMMUNITY_POST,
    /** A member asked to join a private community the recipient moderates. */
    COMMUNITY_REQUEST,
    /** A moderator let the recipient into a private community. */
    COMMUNITY_ACCEPTED,
    /** The creator named the recipient a moderator of their community. */
    COMMUNITY_MODERATOR,
    /** A member passed on a mission matching the recipient's card. */
    MISSION_MATCH,
    /** An idea of the ideas board the recipient voted for moved on: planned, being built, shipped, declined or merged. */
    IDEA_STATUS,
    /** An event the recipient goes to or is interested in starts within a day. */
    MEETUP_REMINDER,
    /** An event the recipient goes to or is interested in was cancelled. */
    MEETUP_CANCELLED,
    /** A contact the recipient invited joined the network. */
    INVITE_JOINED,
    /** The filter held the recipient's review of a company for the moderators. */
    REVIEW_HELD,
    /** The moderators published the recipient's held review. */
    REVIEW_PUBLISHED,
    /** The moderators rejected the recipient's held review. */
    REVIEW_REJECTED,
    /** A review waits for the recipient, an administrator, to publish or reject it. */
    REVIEW_PENDING
}
