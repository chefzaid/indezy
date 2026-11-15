package dev.swirlit.indezy.model.enums;

/** What a post is, each with its own card (see docs/features.md#member-network). */
public enum NetworkPostKind {
    /** A plain post. */
    POST,
    /** A mission the author declines or cannot take: role, rate range, city, remote days, start; the client if named. */
    MISSION,
    /** The author's next availability, stack and rate range. */
    AVAILABLE,
    /** A question, marked answered when the author picks a reply. */
    QUESTION,
    /** What a mission, an ESN's process or a tool taught. */
    EXPERIENCE,
    /** Two to four choices, open one to seven days. */
    POLL
}
