package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferWordSuggestionDto;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferWordSuggestionServiceTest {

    private final JobOfferStateRepository states = mock(JobOfferStateRepository.class);
    private final OfferWordSuggestionService service = new OfferWordSuggestionService(states,
        Clock.fixed(Instant.parse("2026-11-22T10:00:00Z"), ZoneOffset.UTC));

    private void hidden(List<String> texts) {
        when(states.findHiddenSearchTexts(eq(3L), eq(LocalDateTime.parse("2026-10-23T10:00:00")), any())).thenReturn(texts);
    }

    @Test
    void offersTheSectorWordsTheHiddenOffersKeepMentioningTheMostMentionedFirstOnePerSector() {
        List<String> texts = new ArrayList<>();
        texts.addAll(Collections.nCopies(4, "developpeur java banque de detail"));
        texts.addAll(Collections.nCopies(3, "architecte cloud groupe bancaire"));
        texts.addAll(Collections.nCopies(7, "devops secteur defense"));
        texts.addAll(Collections.nCopies(7, "operateur telecoms"));
        texts.addAll(Collections.nCopies(6, "tech lead assurance vie"));
        hidden(texts);
        when(states.findKeptSearchTexts(eq(3L), any())).thenReturn(List.of("data engineer assurance sante"));

        // Insurance stays: an offer kept mentions it. Banking counts its words apart, neither enough on its own.
        assertThat(service.suggestions(3L)).containsExactly(new OfferWordSuggestionDto("defense", "defense", 7),
            new OfferWordSuggestionDto("telecom", "telecoms", 7));
    }

    @Test
    void takesNoWordAQuarterOfTheHiddenOffersDoNotMentionNorAnyWhenFewOffersWereHidden() {
        List<String> texts = new ArrayList<>(Collections.nCopies(5, "devops secteur defense"));
        texts.addAll(Collections.nCopies(20, "developpeur java"));
        hidden(texts);
        when(states.findKeptSearchTexts(eq(3L), any())).thenReturn(List.of());
        assertThat(service.suggestions(3L)).isEmpty();

        hidden(Collections.nCopies(4, "devops secteur defense"));
        assertThat(service.suggestions(3L)).isEmpty();
        verify(states).findKeptSearchTexts(eq(3L), any());
    }

    @Test
    void readsOnlyTheSectorsWords() {
        assertThat(OfferWordSuggestionService.SECTORS.values()).allSatisfy(words -> assertThat(words)
            .allSatisfy(word -> assertThat(word).matches("[a-z]{5,}")));
    }
}
