package dev.swirlit.indezy.model.enums;

/**
 * Lifecycle status of an opportunity, matching the Kanban columns: first contact, tests and
 * interviews, offer, accepted, and refused (by the freelance or the recruiter; the board shows it
 * before accepted). Each status carries a win probability used to weight pipeline revenue into a
 * forecast: signed contracts count fully, refused ones not at all, and pipeline stages in between
 * by their likelihood of closing. The order is the funnel's, which counts on it.
 */
public enum ProjectStatus {
    CONTACT(0.20),
    INTERVIEW(0.50),
    OFFER(0.80),
    WON(1.00),
    LOST(0.00);

    private final double winProbability;

    ProjectStatus(final double winProbability) {
        this.winProbability = winProbability;
    }

    /** Probability (0..1) that an opportunity in this status will turn into a signed contract. */
    public double getWinProbability() {
        return winProbability;
    }
}
