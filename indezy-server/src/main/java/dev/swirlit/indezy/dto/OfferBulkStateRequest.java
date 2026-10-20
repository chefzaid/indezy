package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The same change to what the workspace did with several offers; a {@code null} field keeps its value. */
public record OfferBulkStateRequest(
    @NotEmpty @Size(max = OfferBulkStateRequest.MAX_OFFERS) List<@NotNull Long> offerIds,
    Boolean saved,
    Boolean hidden,
    Boolean seen) {

    public static final int MAX_OFFERS = 100;

    public OfferBulkStateRequest {
        offerIds = offerIds == null ? null : List.copyOf(offerIds);
    }
}
