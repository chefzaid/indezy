package dev.swirlit.indezy.model.enums;

/** Why a mission's commute has no time: what the freelancer can do about it differs. */
public enum CommuteUnavailable {
    /** The profile has no home address. */
    NO_HOME_ADDRESS,
    /** The client has no address. */
    NO_CLIENT_ADDRESS,
    /** This deployment has no Google Maps key. */
    NO_API_KEY,
    /** Google found no route between the addresses. */
    NOT_FOUND,
    /** Google could not be asked; the next request tries again. */
    LOOKUP_FAILED
}
