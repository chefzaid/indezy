package dev.swirlit.indezy.constants;

/** Defaults shared by stored account settings and workspace projections. */
public final class UserPreferenceDefaults {
    public static final int INACTIVITY_DAYS = 14;
    /** Days a message sent waits for an answer before the dashboard says to follow up. */
    public static final int FOLLOW_UP_DAYS = 5;

    private UserPreferenceDefaults() {
    }
}
