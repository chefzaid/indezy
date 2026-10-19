package dev.swirlit.indezy.dto;

/** What the caller's workspace did with an offer. */
public record OfferStateDto(Long offerId, boolean saved, boolean hidden, boolean seen, Long importedProjectId) {
}
