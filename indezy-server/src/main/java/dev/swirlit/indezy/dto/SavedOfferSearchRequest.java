package dev.swirlit.indezy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The feed's current filters, to save under a name. */
public record SavedOfferSearchRequest(@NotBlank @Size(max = 80) String name, @NotNull @Valid OfferSearchRequest filters) {
}
