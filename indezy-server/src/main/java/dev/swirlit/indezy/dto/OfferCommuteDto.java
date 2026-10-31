package dev.swirlit.indezy.dto;

/**
 * The trip from the profile's city to an offer's, estimated from the straight line as the commute of a mission without
 * an address is (see docs/features.md#freelance-offer-feed): minutes off-peak and at rush hour (a weekday at 8:30), by car and by
 * public transport.
 */
public record OfferCommuteDto(int carMinutes, int carRushMinutes, int transitMinutes, int transitRushMinutes) {
}
