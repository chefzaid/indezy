package dev.swirlit.indezy.model.enums;

/**
 * Lifecycle status of an opportunity, matching the Kanban columns: first contact, tests and
 * interviews, offer, accepted, abandoned (the recruiter turned it down) and withdrawn (the
 * freelance did). The order up to accepted is the funnel's, which counts on it.
 */
public enum ProjectStatus {
    CONTACT,
    INTERVIEW,
    OFFER,
    WON,
    /** Abandoned: the recruiter (the client or the ESN) turned it down. */
    LOST,
    /** Withdrawn: the freelance turned it down. */
    WITHDRAWN;

    /** Whether the opportunity was turned down, by either side. */
    public boolean isRefused() {
        return this == LOST || this == WITHDRAWN;
    }
}
