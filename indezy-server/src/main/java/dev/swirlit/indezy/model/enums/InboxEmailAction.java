package dev.swirlit.indezy.model.enums;

/** What the inbox watcher did with a forwarded email (see docs/features.md#inbox-watcher). */
public enum InboxEmailAction {
    /** Recorded only: nothing to act on, no AI, or no opportunity found. */
    NONE,
    /** An interview step added to the opportunity, moved to Interview, and an answer drafted. */
    INTERVIEW_STEP,
    /** The opportunity moved to Abandoned, the reason guessed. */
    MARKED_LOST,
    /** An answer drafted, for the member to send. */
    DRAFTED,
    /** Sent on to the member's own address (the forwarding confirmation). */
    FORWARDED
}
