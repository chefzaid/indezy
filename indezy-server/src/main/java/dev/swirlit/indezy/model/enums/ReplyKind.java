package dev.swirlit.indezy.model.enums;

/** What an answer to an email sent from Indezy says, as the AI sorts it (see docs/features.md#emails-sent-from-indezy). */
public enum ReplyKind {
    /** Interested: a mission to talk about, a call to book, a resume asked for. */
    INTERESTED,
    /** Not now: nothing at the moment, maybe later. */
    NOT_NOW,
    /** No need for the profile. */
    NO_NEED,
    /** Asks not to be written to again. */
    DO_NOT_CONTACT,
    /** Anything else: an out-of-office, a question. */
    OTHER
}
