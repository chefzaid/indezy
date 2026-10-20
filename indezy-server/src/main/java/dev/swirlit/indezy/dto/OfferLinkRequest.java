package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The link of an offer, pasted to make an opportunity of it. */
public record OfferLinkRequest(@NotBlank @Size(max = 2000) String url) {
}
