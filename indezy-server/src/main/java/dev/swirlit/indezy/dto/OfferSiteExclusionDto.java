package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.OfferSite;

/** Whether a workspace keeps a job board out of its offer feed. */
public record OfferSiteExclusionDto(OfferSite site, boolean excluded) {
}
