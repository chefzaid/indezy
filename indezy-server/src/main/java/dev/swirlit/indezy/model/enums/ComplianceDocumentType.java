package dev.swirlit.indezy.model.enums;

/**
 * What a compliance document is, and how long it usually stays valid from its issue date: an URSSAF vigilance
 * certificate six months, a company registration extract three months (what ESNs ask for); the others carry their own
 * end date, or none.
 */
public enum ComplianceDocumentType {
    URSSAF_VIGILANCE(6),
    COMPANY_REGISTRATION(3),
    PROFESSIONAL_INSURANCE(null),
    BANK_DETAILS(null),
    IDENTITY(null),
    OTHER(null);

    private final Integer validityMonths;

    ComplianceDocumentType(Integer validityMonths) {
        this.validityMonths = validityMonths;
    }

    /** Months the document usually stays valid from its issue date, or none. */
    public Integer getValidityMonths() {
        return validityMonths;
    }
}
