package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Offers to add to the pipeline at once. */
public record OfferBulkImportRequest(@NotEmpty @Size(max = OfferBulkImportRequest.MAX_OFFERS) List<@NotNull Long> offerIds) {

    public static final int MAX_OFFERS = 50;

    public OfferBulkImportRequest {
        offerIds = offerIds == null ? null : List.copyOf(offerIds);
    }
}
