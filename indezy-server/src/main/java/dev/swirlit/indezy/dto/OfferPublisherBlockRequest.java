package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The publisher (an intermediary or a client) to keep out of the workspace's feed, as offers name it. */
public record OfferPublisherBlockRequest(@NotBlank @Size(max = 200) String name) {
}
