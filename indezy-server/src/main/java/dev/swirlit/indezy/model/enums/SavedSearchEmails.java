package dev.swirlit.indezy.model.enums;

/** How often a saved search emails the offers Indezy found for it (see docs/features.md#saved-search-emails). */
public enum SavedSearchEmails {
    /** No email. */
    OFF,
    /** The new offers grouped every 30 minutes. */
    INSTANT,
    /** One digest a day, at the search's hour (Paris time). */
    DAILY
}
