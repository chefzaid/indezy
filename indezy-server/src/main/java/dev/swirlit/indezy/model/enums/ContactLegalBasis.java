package dev.swirlit.indezy.model.enums;

/** Why the freelancer may keep a contact's personal data (GDPR article 6): the freelancer is its controller. */
public enum ContactLegalBasis {
    /** Business contacts kept to follow up exchanges: the usual basis for B2B prospecting. */
    LEGITIMATE_INTEREST,
    /** The person agreed to be kept. */
    CONSENT,
    /** Needed for a contract with the person (a mission signed with them). */
    CONTRACT
}
