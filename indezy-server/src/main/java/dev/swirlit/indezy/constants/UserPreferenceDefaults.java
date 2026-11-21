package dev.swirlit.indezy.constants;

/** Defaults shared by stored account settings and workspace projections. */
public final class UserPreferenceDefaults {
    public static final int INACTIVITY_DAYS = 14;
    /** Days a message sent waits for an answer before the dashboard says to follow up. */
    public static final int FOLLOW_UP_DAYS = 5;
    /** Years without exchange before a new account's contacts are anonymized, as the CNIL advises for prospects. */
    public static final int NEW_ACCOUNT_CONTACT_RETENTION_YEARS = 3;

    private UserPreferenceDefaults() {
    }
}
