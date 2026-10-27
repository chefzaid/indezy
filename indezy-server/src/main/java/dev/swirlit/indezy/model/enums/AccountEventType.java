package dev.swirlit.indezy.model.enums;

/** What an account's activity log records. */
public enum AccountEventType {
    SIGNED_IN,
    /** A wrong password or two-factor code for this account. */
    SIGN_IN_REFUSED,
    SIGNED_OUT,
    SESSION_REVOKED,
    OTHER_SESSIONS_REVOKED,
    PASSWORD_CHANGED,
    TWO_FACTOR_ENABLED,
    TWO_FACTOR_DISABLED,
    DATA_EXPORTED,
    DATA_IMPORTED,
    ACCOUNT_DISABLED,
    ACCOUNT_ENABLED,
    API_TOKEN_CREATED,
    API_TOKEN_REVOKED,
    /** A contact's data handed out on their request; the detail names the contact by id only. */
    CONTACT_DATA_EXPORTED,
    /** A contact erased on their request; the detail names the contact by id only. */
    CONTACT_ERASED
}
