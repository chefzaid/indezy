package dev.swirlit.indezy.model.enums;

/** How a session was opened. */
public enum SignInMethod {
    PASSWORD,
    KEYCLOAK,
    REGISTRATION,
    /** The email confirmation link, or a new password set from the reset link. */
    EMAIL_LINK
}
