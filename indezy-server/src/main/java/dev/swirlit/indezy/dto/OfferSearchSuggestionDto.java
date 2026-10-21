package dev.swirlit.indezy.dto;

import lombok.Value;

/**
 * A search suggested from the profile and the pipeline, with what it finds in the feed now: its open offers, those
 * scoring {@code OfferMatch#GOOD_MATCH} or more, and those published in the last seven days. {@code place} names the
 * profile's city when the filters measure a distance from it, or the region they keep.
 */
@Value
public class OfferSearchSuggestionDto {
    String key;
    OfferSearchRequest filters;
    String place;
    long offers;
    long goodMatches;
    long newThisWeek;
}
