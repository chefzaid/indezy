package dev.swirlit.indezy.model.enums;

/**
 * Lifecycle status of an opportunity, matching the Kanban columns: first contact, tests and
 * interviews, offer, accepted, and refused (by the freelance or the recruiter; the board shows it
 * before accepted). The order is the funnel's, which counts on it.
 */
public enum ProjectStatus {
    CONTACT,
    INTERVIEW,
    OFFER,
    WON,
    LOST
}
