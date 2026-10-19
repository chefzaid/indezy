package dev.swirlit.indezy.dto;

/** Changes to what the workspace did with an offer; a {@code null} field keeps its value. */
public record OfferStateUpdateRequest(Boolean saved, Boolean hidden, Boolean seen) {
}
