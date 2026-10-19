package dev.swirlit.indezy.model.enums;

/**
 * Lifecycle of a monthly activity report (CRA): the freelance edits a draft, submits it to the client or ESN through a
 * validation link, and the validator approves or rejects it. A rejected report can be edited and submitted again.
 */
public enum ActivityReportStatus {
    DRAFT,
    SUBMITTED,
    VALIDATED,
    REJECTED;

    /** Days and comment can change only before submission or after a rejection. */
    public boolean isEditable() {
        return this == DRAFT || this == REJECTED;
    }
}
