package dev.swirlit.indezy.model.enums;

/** How a dossier de compétences is laid out. */
public enum DossierStyle {
    /** The French dossier de compétences: room around each section, every mission in full. */
    CLASSIC,
    /** Tighter, for one or two pages: smaller type, the 8 latest missions, each description cut at 300 characters. */
    COMPACT
}
