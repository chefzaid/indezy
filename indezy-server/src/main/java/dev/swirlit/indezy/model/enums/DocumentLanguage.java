package dev.swirlit.indezy.model.enums;

import java.util.Locale;

/** Language of a generated document such as an activity report PDF. */
public enum DocumentLanguage {
    FR,
    EN;

    /** The locale dates, numbers and amounts are written in: British English or French. */
    public Locale locale() {
        return this == EN ? Locale.UK : Locale.FRANCE;
    }
}
