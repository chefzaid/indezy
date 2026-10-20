package dev.swirlit.indezy.model.enums;

/** What the link of an offer gave: see docs/features.md#from-an-offer-link. */
public enum OfferLinkStatus {
    /** The offer is in the feed, and not in the pipeline yet. */
    FOUND,
    /** The workspace already has an opportunity for the mission, by this offer or one of its copies. */
    IN_PIPELINE,
    /** The offer is not in the feed yet, and its site is being read. */
    READING,
    /** The offer is not in the feed, and its site cannot be read on request now. */
    NOT_COLLECTED,
    /** The link is on none of the sites the feed reads. */
    UNSUPPORTED_SITE,
    /** A permanent or fixed-term job, or a mission closed to freelancers. */
    NOT_FREELANCE,
    /** Expired, or published longer ago than the feed keeps offers. */
    EXPIRED,
    /** The page is gone or holds no offer. */
    GONE,
    /** The site could not be read: unreachable, an error, or its {@code robots.txt} disallows the page. */
    SITE_UNAVAILABLE
}
