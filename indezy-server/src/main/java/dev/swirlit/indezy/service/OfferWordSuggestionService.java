package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferWordSuggestionDto;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Words naming a sector that the workspace's hidden offers keep mentioning, offered to leave out of its feed (the
 * feed's "words to leave out" filter, which leaves out every offer whose text holds the word). Only a fixed list of
 * sector words is read, so a suggestion never names a skill or a client.
 */
@Service
@RequiredArgsConstructor
public class OfferWordSuggestionService {

    /** A word is offered once this many offers hidden within {@link #DAYS} days mention it. */
    static final int HIDDEN_MIN = 5;
    /** ... and at least this share of them, so a word every offer mentions is not taken for a pattern. */
    static final double HIDDEN_SHARE = 0.25;
    static final int DAYS = 30;
    private static final int READ_HIDDEN = 500;
    private static final int READ_KEPT = 500;
    private static final int SUGGESTIONS_MAX = 2;

    /**
     * Each sector's words, folded as offers' search text is, each read as the filter reads it: anywhere in the text.
     * A word is left out when it would also match common unrelated words (no "luxe", which "Luxembourg" holds).
     */
    static final Map<String, List<String>> SECTORS = Map.ofEntries(
        Map.entry("banking", List.of("banque", "bancaire")),
        Map.entry("insurance", List.of("assurance", "assureur", "mutuelle")),
        Map.entry("defense", List.of("defense", "armement")),
        Map.entry("energy", List.of("energie", "nucleaire", "petrole")),
        Map.entry("health", List.of("pharma", "hopital", "hospitalier")),
        Map.entry("retail", List.of("retail", "distribution")),
        Map.entry("telecoms", List.of("telecom")),
        Map.entry("publicSector", List.of("collectivite", "ministere")),
        Map.entry("automotive", List.of("automobile", "automotive")),
        Map.entry("aerospace", List.of("aeronautique", "aerospatial", "aerospace")),
        Map.entry("rail", List.of("ferroviaire")),
        Map.entry("gaming", List.of("casino", "betting")),
        Map.entry("crypto", List.of("crypto", "blockchain")),
        Map.entry("realEstate", List.of("immobilier")),
        Map.entry("food", List.of("agroalimentaire")));

    private final JobOfferStateRepository states;
    private final Clock clock;

    /**
     * The sector words that {@value #HIDDEN_MIN} or more of the offers hidden in the last {@value #DAYS} days mention, a
     * quarter of them at least, that no offer kept or applied to mentions; the most mentioned first, one per sector, two
     * at most.
     */
    @Transactional(readOnly = true)
    public List<OfferWordSuggestionDto> suggestions(Long freelanceId) {
        List<String> hidden = states.findHiddenSearchTexts(freelanceId, LocalDateTime.now(clock).minusDays(DAYS), PageRequest.of(0, READ_HIDDEN));
        if (hidden.size() < HIDDEN_MIN) {
            return List.of();
        }
        List<String> kept = states.findKeptSearchTexts(freelanceId, PageRequest.of(0, READ_KEPT));
        long needed = Math.max(HIDDEN_MIN, (long) Math.ceil(hidden.size() * HIDDEN_SHARE));
        List<OfferWordSuggestionDto> found = new ArrayList<>();
        SECTORS.forEach((sector, words) -> words.stream()
            .filter(word -> kept.stream().noneMatch(text -> text.contains(word)))
            .map(word -> new OfferWordSuggestionDto(word, sector, hidden.stream().filter(text -> text.contains(word)).count()))
            .filter(suggestion -> suggestion.hidden() >= needed)
            .max(Comparator.comparingLong(OfferWordSuggestionDto::hidden).thenComparing(OfferWordSuggestionDto::word, Comparator.reverseOrder()))
            .ifPresent(found::add));
        return found.stream().sorted(Comparator.comparingLong(OfferWordSuggestionDto::hidden).reversed().thenComparing(OfferWordSuggestionDto::word))
            .limit(SUGGESTIONS_MAX).toList();
    }
}
