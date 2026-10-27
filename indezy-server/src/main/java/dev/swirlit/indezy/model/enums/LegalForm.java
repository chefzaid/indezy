package dev.swirlit.indezy.model.enums;

/** How a freelancer's business is set up, which decides what its invoices must state. */
public enum LegalForm {
    /** An individual entrepreneur under the micro-enterprise regime: an EI, usually exempt from VAT. */
    MICRO_ENTREPRISE,
    /** An individual entrepreneur (EI) under the real regime. */
    EI,
    EURL,
    SASU,
    SARL,
    SAS,
    /** Paid through a portage company, which invoices the client itself. */
    PORTAGE,
    OTHER;

    /** Whether it is an individual entrepreneur, whose name must be followed by "EI" on invoices. */
    public boolean isIndividual() {
        return this == MICRO_ENTREPRISE || this == EI;
    }

    /** Whether it is a company, whose invoices state its share capital and registration. */
    public boolean isCompany() {
        return this == EURL || this == SASU || this == SARL || this == SAS;
    }
}
