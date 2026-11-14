package dev.swirlit.indezy.model.enums;

/** How a member's card tells their availability: from their won missions, or as they set it. */
public enum NetworkAvailability {
    /** On a won mission until its last day, else available from the profile's availability date. */
    AUTO,
    /** Available, from a day or now. */
    AVAILABLE,
    /** On a mission, until a day or with no end said. */
    ON_MISSION,
    /** Not looking, but open to hear of a mission ("À l'écoute"). */
    LISTENING
}
