package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * How well an offer fits the workspace's profile (see {@code OfferMatch}).
 *
 * @param score  out of 100
 * @param skills the profile's skills the offer mentions, as the profile spells them
 * @param rate   how the offer's daily rate compares with the target one; absent when the profile sets none
 * @param place  how far the mission is from the profile's city; absent when the profile names none
 * @param availability when the mission starts against when the freelance is available; absent when the profile does
 *                     not say
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferMatchDto(int score, List<String> skills, RateFit rate, PlaceFit place, AvailabilityFit availability) {

    public enum RateFit {
        /** At or above the target. */
        MEETS,
        /** Less than 10 percent below. */
        CLOSE,
        BELOW,
        /** The offer states no rate. */
        UNKNOWN
    }

    public enum PlaceFit {
        REMOTE,
        /** Within 30 km, or within the profile's longest commute at rush hour by the estimate. */
        NEAR,
        /** Within 80 km, or within one and a half times that commute. */
        REACHABLE,
        /** Farther, with three remote days a week or more. */
        MOSTLY_REMOTE,
        FAR,
        /** The offer states no place. */
        UNKNOWN
    }

    public enum AvailabilityFit {
        /** Once the freelance is available. */
        READY,
        /** Up to two weeks before. */
        SOON,
        /** Earlier than that. */
        TOO_EARLY,
        /** The offer states no start. */
        UNKNOWN
    }

    public OfferMatchDto {
        skills = List.copyOf(skills);
    }
}
