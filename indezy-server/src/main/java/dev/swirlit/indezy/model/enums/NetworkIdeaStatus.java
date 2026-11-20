package dev.swirlit.indezy.model.enums;

/** Where an idea of the ideas board stands, set by the administrators. */
public enum NetworkIdeaStatus {
    /** Proposed, open to votes, not yet answered. */
    OPEN,
    /** On the roadmap. */
    PLANNED,
    /** Being built. */
    IN_PROGRESS,
    /** Shipped. */
    DONE,
    /** Not planned, the administrators saying why. */
    DECLINED,
    /** A duplicate, its votes moved to the idea it repeats. */
    MERGED
}
