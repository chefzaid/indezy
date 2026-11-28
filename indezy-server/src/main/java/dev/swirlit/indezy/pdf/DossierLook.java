package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.enums.DossierStyle;

/** How a dossier looks: its layout and its accent colour ({@code #RRGGBB}, or none for black). */
public record DossierLook(DossierStyle style, String accent) {

    public static final DossierLook CLASSIC = new DossierLook(DossierStyle.CLASSIC, null);

    public DossierLook {
        style = style == null ? DossierStyle.CLASSIC : style;
        accent = accent != null && accent.matches("#[0-9A-Fa-f]{6}") ? accent.toUpperCase(java.util.Locale.ROOT) : null;
    }

    boolean compact() {
        return style == DossierStyle.COMPACT;
    }
}
