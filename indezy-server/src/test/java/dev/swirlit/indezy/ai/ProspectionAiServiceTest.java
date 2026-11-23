package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.service.ContactEmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProspectionAiServiceTest {

    private final AiService ai = mock(AiService.class);
    private final ContactEmailService emails = mock(ContactEmailService.class);
    private final ContactInteractionRepository interactions = mock(ContactInteractionRepository.class);
    private final ProspectionAiService service = new ProspectionAiService(ai, emails, interactions);

    private static ContactEmailService.Recipient recipient(long id, String firstName, String company, String mission) {
        return new ContactEmailService.Recipient(id, firstName, firstName + " Martin", company, firstName.toLowerCase() + "@example.com", 2, null,
            mission == null ? 0 : 1, mission, 0, 0, null);
    }

    @Test
    void writesEachTickedContactsOwnMessageFromWhatIsKnownOfThem() {
        when(emails.availabilityRecipients(5L)).thenReturn(List.of(recipient(1, "Léa", "Conseil X", "Lead Java"),
            recipient(2, "Paul", "ESN Y", null), recipient(3, "Zoé", "ESN Z", null)));
        ContactInteraction exchange = new ContactInteraction();
        exchange.setContact(new Contact());
        exchange.setOccurredOn(LocalDate.of(2026, 9, 1));
        exchange.setDirection(InteractionDirection.INCOMING);
        exchange.setSummary("Merci, je reviens vers vous en janvier");
        when(interactions.findByContactIdOrderByOccurredOnDescIdDesc(anyLong())).thenReturn(List.of());
        when(interactions.findByContactIdOrderByOccurredOnDescIdDesc(1L)).thenReturn(List.of(exchange));
        // The AI answers a contact twice, one not asked and an empty message: only the first of each asked one is kept.
        when(ai.structured(eq(7L), eq(AiFeature.MESSAGE), any(), any(), eq(ProspectionAiService.Answer.class), anyLong(), eq(OutputConfig.Effort.LOW)))
            .thenReturn(new ProspectionAiService.Answer(List.of(new ProspectionAiService.Item(1L, " Bonjour Léa, "),
                new ProspectionAiService.Item(1L, "again"), new ProspectionAiService.Item(3L, "Bonjour Zoé"),
                new ProspectionAiService.Item(2L, " "))));

        List<ProspectionAiService.Written> written = service.write(7L, 5L, "Disponible en février", "Bonjour {{firstName}},", List.of(1L, 2L, 9L),
            DocumentLanguage.FR);

        assertThat(written).containsExactly(new ProspectionAiService.Written(1L, "Bonjour Léa,"));
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        verify(ai).structured(eq(7L), eq(AiFeature.MESSAGE), any(), context.capture(), eq(ProspectionAiService.Answer.class), anyLong(),
            eq(OutputConfig.Effort.LOW));
        assertThat(context.getValue()).contains("Answer in French", "Subject: Disponible en février", "<contact id=\"1\">", "First name: Léa",
            "Company: Conseil X", "Newest mission their company posted for this profile: Lead Java",
            "Exchange on 2026-09-01 (from them): Merci, je reviens vers vous en janvier", "<contact id=\"2\">").doesNotContain("Zoé");
    }

    @Test
    void refusesWhenNoneOfTheContactsCanBeWrittenTo() {
        when(emails.availabilityRecipients(5L)).thenReturn(List.of(recipient(1, "Léa", "Conseil X", null)));
        assertThatThrownBy(() -> service.write(7L, 5L, "s", "b", List.of(9L), DocumentLanguage.EN)).isInstanceOf(IllegalArgumentException.class);
    }
}
