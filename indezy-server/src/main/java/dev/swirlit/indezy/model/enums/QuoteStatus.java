package dev.swirlit.indezy.model.enums;

/** Where a quote stands; a sent quote past its validity reads as expired. */
public enum QuoteStatus {
    /** Being written: no number, can change or go. */
    DRAFT,
    /** Numbered and sent to the client: it can no longer change. */
    SENT,
    ACCEPTED,
    REFUSED
}
