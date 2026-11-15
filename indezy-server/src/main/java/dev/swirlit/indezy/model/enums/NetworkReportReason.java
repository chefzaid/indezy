package dev.swirlit.indezy.model.enums;

/** Why a member reports a post, a comment or a member. */
public enum NetworkReportReason {
    SPAM,
    HARASSMENT,
    HATE,
    MISLEADING,
    /** Names a client, a rate or a mission under confidentiality. */
    CONFIDENTIAL,
    IMPERSONATION,
    OTHER
}
