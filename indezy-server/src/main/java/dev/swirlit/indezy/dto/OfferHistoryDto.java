package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.swirlit.indezy.model.enums.OfferChangeField;

import java.time.Instant;
import java.util.List;

/**
 * What a mission's history says (see docs/features.md#mission-history): how many times its site published it under a
 * new reference in 60 days, when more than once, and what the site changed on the offer, the latest first.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferHistoryDto(Integer publishedTimes, List<Change> changes) {

    public OfferHistoryDto {
        changes = changes == null || changes.isEmpty() ? null : List.copyOf(changes);
    }

    /** A change: the values as {@link OfferChangeField} writes them. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Change(OfferChangeField field, String before, String after, Instant changedAt) {
    }
}
