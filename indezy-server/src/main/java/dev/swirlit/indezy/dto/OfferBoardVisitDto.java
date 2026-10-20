package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.OfferLeftOutSite;

import java.time.Instant;

/** When the workspace last visited a site Indezy does not read; {@code visitedAt} is {@code null} once cleared. */
public record OfferBoardVisitDto(OfferLeftOutSite site, Instant visitedAt) {
}
