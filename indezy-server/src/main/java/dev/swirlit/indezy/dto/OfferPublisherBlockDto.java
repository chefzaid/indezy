package dev.swirlit.indezy.dto;

/** A publisher whose offers the workspace keeps out of its feed, under the name it was blocked with. */
public record OfferPublisherBlockDto(Long id, String name) {
}
