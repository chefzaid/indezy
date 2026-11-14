package dev.swirlit.indezy.model.enums;

/**
 * How a dossier de compétences presents its author: named, with contact details and the clients' names, or
 * anonymized, as ESNs ask before presenting a profile to a client (initials, no contact, clients unnamed).
 */
public enum DossierLayout {
    NAMED,
    ANONYMOUS
}
