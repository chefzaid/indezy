package dev.swirlit.indezy.model.enums;

/** A micro-entrepreneur's activity, which sets the contribution rate (see docs/features.md#urssaf-contributions). */
public enum MicroActivity {
    /** A non-regulated liberal activity (BNC) under the SSI: most IT consultants. */
    BNC_SSI,
    /** A regulated liberal activity (BNC) under the CIPAV. */
    BNC_CIPAV,
    /** Commercial services (BIC). */
    BIC_SERVICES
}
