package dev.swirlit.indezy.dto;

import lombok.Value;

import java.time.Instant;

/** A suggested search the workspace dismissed, for the account data export. */
@Value
public class OfferSuggestionDismissalDto {
    OfferSearchRequest filters;
    Instant dismissedAt;
}
