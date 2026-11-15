package dev.swirlit.indezy.model.enums;

/** What a network notification tells its recipient. */
public enum NetworkNotificationType {
    /** A member followed them. */
    FOLLOW,
    /** A member liked their post. */
    LIKE,
    /** A member commented their post. */
    COMMENT,
    /** A member replied to their comment. */
    REPLY,
    /** A member mentioned them in a post or a comment. */
    MENTION,
    /** A member reposted their post. */
    REPOST
}
