package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.model.enums.ReplyKind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplySortingAiServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private final AiService ai = mock(AiService.class);
    private final ReplySortingAiService sorting = new ReplySortingAiService(ai);

    @Test
    void sortsTheAnswerOnTheTriageModelFromItsStartAndReadsNothingAsOther() {
        when(ai.structured(eq(3L), eq(AiFeature.TRIAGE), any(), any(), eq(ReplySortingAiService.Sorted.class), anyLong(), any()))
            .thenReturn(new ReplySortingAiService.Sorted(ReplyKind.INTERESTED, "2027-03-01"));
        assertThat(sorting.sort(3L, "Re: Mission Java", "Oui, appelons-nous jeudi." + "x".repeat(5_000), TODAY))
            .isEqualTo(new ReplySortingAiService.Sort(ReplyKind.INTERESTED, null));
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(ai).structured(eq(3L), eq(AiFeature.TRIAGE), any(), context.capture(), any(), anyLong(), any());
        assertThat(context.getValue()).contains("<today>2026-11-24</today>", "<subject>Re: Mission Java</subject>").hasSizeLessThan(ReplySortingAiService.TEXT_MAX + 100);

        when(ai.structured(eq(4L), any(), any(), any(), eq(ReplySortingAiService.Sorted.class), anyLong(), any()))
            .thenReturn(new ReplySortingAiService.Sorted(null, null));
        assertThat(sorting.sort(4L, null, null, TODAY)).isEqualTo(new ReplySortingAiService.Sort(ReplyKind.OTHER, null));
    }

    @Test
    void keepsTheDayToWriteBackOfANotNowOnlyWhenItIsAReadableDateWithinTwoYears() {
        assertThat(notNow("2027-03-01")).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(notNow("mars")).isNull();
        assertThat(notNow("2026-11-01")).isNull();
        assertThat(notNow("2030-01-01")).isNull();
        assertThat(notNow(" ")).isNull();
    }

    private LocalDate notNow(String writeBackOn) {
        when(ai.structured(eq(5L), any(), any(), any(), eq(ReplySortingAiService.Sorted.class), anyLong(), any()))
            .thenReturn(new ReplySortingAiService.Sorted(ReplyKind.NOT_NOW, writeBackOn));
        ReplySortingAiService.Sort sort = sorting.sort(5L, "Re: Disponible", "Revenez vers moi en mars.", TODAY);
        assertThat(sort.kind()).isEqualTo(ReplyKind.NOT_NOW);
        return sort.writeBackOn();
    }
}
