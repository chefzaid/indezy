package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.model.enums.InboxEmailKind;
import dev.swirlit.indezy.model.enums.LostReason;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboxReadingAiServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final InboxReadingAiService.Profile PROFILE = new InboxReadingAiService.Profile("Alice Martin", LocalDate.of(2027, 1, 4),
        650, "https://indezy.example/book/abc");

    private final AiService ai = mock(AiService.class);
    private final InboxReadingAiService reading = new InboxReadingAiService(ai);

    private InboxReadingAiService.Read read(InboxReadingAiService.Reading answer) {
        when(ai.structured(eq(3L), eq(AiFeature.MESSAGE), any(), any(), eq(InboxReadingAiService.Reading.class), anyLong(), any()))
            .thenReturn(answer);
        return reading.read(3L, "Entretien", "Bonjour", PROFILE, TODAY);
    }

    @Test
    void keepsAnInvitesReadableSlotsAfterTodayInOrderAndItsAnswer() {
        InboxReadingAiService.Read read = read(new InboxReadingAiService.Reading(InboxEmailKind.INTERVIEW_INVITE,
            List.of("2026-12-02T14:00", "jeudi", "2026-11-20T10:00", "2026-12-01T10:00", "2028-01-01T10:00"), null, " Jeudi me va. "));
        assertThat(read.slots()).containsExactly(LocalDateTime.of(2026, 12, 1, 10, 0), LocalDateTime.of(2026, 12, 2, 14, 0));
        assertThat(read.answer()).isEqualTo("Jeudi me va.");
        assertThat(read.reason()).isNull();
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(ai).structured(eq(3L), any(), any(), context.capture(), any(), anyLong(), any());
        assertThat(context.getValue()).contains("<today>2026-11-24</today>", "Available from: 2027-01-04", "650 EUR", "/book/abc");
    }

    @Test
    void givesARejectionItsReasonAndNoAnswerAndReadsNothingAsOther() {
        InboxReadingAiService.Read rejection = read(new InboxReadingAiService.Reading(InboxEmailKind.REJECTION, List.of("2026-12-01T10:00"),
            null, "Merci"));
        assertThat(rejection.reason()).isEqualTo(LostReason.OTHER);
        assertThat(rejection.answer()).isNull();
        assertThat(rejection.slots()).isEmpty();
        assertThat(read(null).kind()).isEqualTo(InboxEmailKind.OTHER);
        assertThat(read(new InboxReadingAiService.Reading(InboxEmailKind.FORWARDING_CONFIRMATION, null, null, null)).kind())
            .isEqualTo(InboxEmailKind.OTHER);
        assertThat(read(new InboxReadingAiService.Reading(InboxEmailKind.AVAILABILITY_ASK, null, null, "  ")).answer()).isNull();
    }
}
