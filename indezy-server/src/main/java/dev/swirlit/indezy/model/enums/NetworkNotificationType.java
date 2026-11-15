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
    REPORT_DISMISSED
}
