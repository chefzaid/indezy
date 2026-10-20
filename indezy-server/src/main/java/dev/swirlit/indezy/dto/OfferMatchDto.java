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
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferMatchDto(int score, List<String> skills, RateFit rate, PlaceFit place) {

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
        /** Within 30 km. */
        NEAR,
        /** Within 80 km. */
        REACHABLE,
        /** Farther, with three remote days a week or more. */
        MOSTLY_REMOTE,
        FAR,
        /** The offer states no place. */
        UNKNOWN
    }

    public OfferMatchDto {
        skills = List.copyOf(skills);
    }
}
