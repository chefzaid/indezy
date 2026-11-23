package dev.swirlit.indezy.model.enums;

/**
 * The font of invoices and quotes, embedded in the PDFs (Factur-X needs them embedded): one of the two bundled
 * open-licence fonts, or the TrueType font the workspace uploaded ({@code DocumentFontFile}).
 */
public enum DocumentFont {
    SANS,
    SERIF,
    CUSTOM
}
