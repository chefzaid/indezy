package dev.swirlit.indezy.dto;

/** A publisher whose offers the workspace keeps out of its feed, under the name it was blocked with. */
public record OfferPublisherBlockDto(Long id, String name) {

    /** A publisher the workspace keeps hiding offers from, offered to block: its name and the offers hidden lately. */
    public record Suggestion(String name, long hidden) {
    }
}
