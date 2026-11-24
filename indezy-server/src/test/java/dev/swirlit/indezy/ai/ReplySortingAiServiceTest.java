package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.model.enums.ReplyKind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReplySortingAiServiceTest {

    private final AiService ai = mock(AiService.class);
    private final ReplySortingAiService sorting = new ReplySortingAiService(ai);

    @Test
    void sortsTheAnswerOnTheTriageModelFromItsStartAndReadsNothingAsOther() {
        when(ai.structured(eq(3L), eq(AiFeature.TRIAGE), any(), any(), eq(ReplySortingAiService.Sorted.class), anyLong(), any()))
            .thenReturn(new ReplySortingAiService.Sorted(ReplyKind.INTERESTED));
        assertThat(sorting.sort(3L, "Re: Mission Java", "Oui, appelons-nous jeudi." + "x".repeat(5_000))).isEqualTo(ReplyKind.INTERESTED);
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(ai).structured(eq(3L), eq(AiFeature.TRIAGE), any(), context.capture(), any(), anyLong(), any());
        assertThat(context.getValue()).contains("<subject>Re: Mission Java</subject>").hasSizeLessThan(ReplySortingAiService.TEXT_MAX + 100);

        when(ai.structured(eq(4L), any(), any(), any(), eq(ReplySortingAiService.Sorted.class), anyLong(), any()))
            .thenReturn(new ReplySortingAiService.Sorted(null));
        assertThat(sorting.sort(4L, null, null)).isEqualTo(ReplyKind.OTHER);
    }
}
