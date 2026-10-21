package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * How an offer's daily rate (the middle of the range it states) compares with the median of the open offers naming
 * the same skill first, in its region or, when the region has too few, in all of France ({@code regionName} absent).
 *
 * @param percent above (positive) or below the median, rounded
 * @param offers  the offers the median is taken over, one per mission
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferRateMarketDto(int percent, int median, int offers, String skill, String regionName) {
}
