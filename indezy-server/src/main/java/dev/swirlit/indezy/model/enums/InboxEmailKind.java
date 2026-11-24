package dev.swirlit.indezy.model.enums;

/** What an email forwarded to the inbox watcher is, as the AI read it (see docs/features.md#inbox-watcher). */
public enum InboxEmailKind {
    /** Asks for an interview, maybe with slots. */
    INTERVIEW_INVITE,
    /** Turns an application down. */
    REJECTION,
    /** Asks whether the freelancer is available, or their rate. */
    AVAILABILITY_ASK,
    /** The mail service asking to confirm the forwarding (Gmail's code). */
    FORWARDING_CONFIRMATION,
    OTHER
}
