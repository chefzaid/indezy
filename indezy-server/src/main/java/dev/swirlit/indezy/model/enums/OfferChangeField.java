package dev.swirlit.indezy.model.enums;

/** What changed on an offer its site published again with other details (see docs/features.md#mission-history). */
public enum OfferChangeField {
    /** Its daily rate: the values are "min|max", either side empty when not stated. */
    RATE,
    /** Its start: the values are an ISO day, "ASAP", or empty. */
    START,
    /** Its description, without values. */
    DESCRIPTION
}
