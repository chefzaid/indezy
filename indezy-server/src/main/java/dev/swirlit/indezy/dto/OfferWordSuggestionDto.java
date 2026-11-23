package dev.swirlit.indezy.dto;

/**
 * A word the workspace's hidden offers keep mentioning, offered to leave out of its feed: the word as the feed's
 * "words to leave out" filter takes it, the sector it names, and how many of the offers hidden lately mention it.
 */
public record OfferWordSuggestionDto(String word, String sector, long hidden) {
}
