package dev.swirlit.indezy.model.enums;

/** How a job offer's publication date was obtained. */
public enum OfferDatePrecision {
    /** The site gives the publication time. */
    EXACT,
    /** The site gives the publication day only. */
    DAY,
    /** The site gives no date: the offer is dated when Indezy first saw it. */
    FIRST_SEEN
}
