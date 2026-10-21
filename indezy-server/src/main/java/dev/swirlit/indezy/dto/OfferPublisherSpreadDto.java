package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A mission several intermediaries publish: how many, and the lowest and highest of the best rates they state (absent
 * when none states one).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferPublisherSpreadDto(int publishers, Integer rateMin, Integer rateMax) {
}
