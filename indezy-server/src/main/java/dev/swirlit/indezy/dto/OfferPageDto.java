package dev.swirlit.indezy.dto;

import java.util.List;

/**
 * One page of the offer feed.
 *
 * @param distanceAvailable whether the profile's city is known, so distances can be shown and filtered on
 * @param matchAvailable    whether the profile names skills, so offers are scored against it
 */
public record OfferPageDto(List<OfferDto> content, int page, int size, long totalElements, int totalPages,
                           boolean distanceAvailable, boolean matchAvailable) {

    public OfferPageDto {
        content = List.copyOf(content);
    }
}
