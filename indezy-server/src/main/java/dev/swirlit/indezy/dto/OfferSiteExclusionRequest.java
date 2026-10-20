package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotNull;

/** Keeps a job board out of the workspace's offer feed ({@code true}) or brings it back. */
public record OfferSiteExclusionRequest(@NotNull Boolean excluded) {
}
