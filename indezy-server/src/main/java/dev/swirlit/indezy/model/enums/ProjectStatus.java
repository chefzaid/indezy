package dev.swirlit.indezy.model.enums;

/**
 * Lifecycle status of an opportunity. Each status carries a win probability used to
 * weight pipeline revenue into a forecast: signed contracts count fully, lost ones
 * not at all, and pipeline stages in between by their likelihood of closing.
 */
public enum ProjectStatus {
    IDENTIFIED(0.10),
    APPLIED(0.25),
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
