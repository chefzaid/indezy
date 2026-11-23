package dev.swirlit.indezy.model.enums;

/**
 * The layout of invoices and quotes (see docs/features.md#billing-identity): Simple (black on white), Modern (a band of
 * the accent colour across the top with the title in white, the table header and the amount to pay filled with it), or
 * Classic (a serif font, the logo centred, thin rules under the header and above the footer, alternate shaded rows and
 * the bank details framed).
 */
public enum DocumentTheme {
    SIMPLE,
    MODERN,
    CLASSIC
}
