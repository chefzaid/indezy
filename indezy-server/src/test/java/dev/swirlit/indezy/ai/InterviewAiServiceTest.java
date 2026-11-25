package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.dto.CompanyHealthDto;
import dev.swirlit.indezy.model.Client;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewAiServiceTest {

    @Test
    void tellsTheCoachWhatTheRegistersSayOfTheCompany() {
        Client client = new Client();
        client.setCompanyName("Banque Exemple");

        assertThat(InterviewAiService.health(client, CompanyHealthDto.UNCHECKED)).isEmpty();
        assertThat(InterviewAiService.health(client, new CompanyHealthDto(true, false, null, null)))
            .contains("Health of Banque Exemple: in business, no insolvency proceedings");
        assertThat(InterviewAiService.health(client, new CompanyHealthDto(true, true, null, null)))
            .contains("Health of Banque Exemple: closed");
        assertThat(InterviewAiService.health(client, new CompanyHealthDto(true, false, CompanyHealthDto.Procedure.REDRESSEMENT,
            LocalDate.of(2026, 3, 2)))).contains("Health of Banque Exemple: under insolvency proceedings (redressement) since 2026-03-02");
    }

    @Test
    void readsADebriefIntoWhatComesNextKeepingTheNextRoundOnlyWhenThereIsOne() {
        AiService ai = org.mockito.Mockito.mock(AiService.class);
        dev.swirlit.indezy.repository.ProjectRepository projects = org.mockito.Mockito.mock(dev.swirlit.indezy.repository.ProjectRepository.class);
        dev.swirlit.indezy.repository.InterviewStepRepository steps = org.mockito.Mockito.mock(dev.swirlit.indezy.repository.InterviewStepRepository.class);
        dev.swirlit.indezy.model.Project project = new dev.swirlit.indezy.model.Project();
        project.setRole("Lead Java");
        project.setClient(new Client());
        org.mockito.Mockito.when(projects.findById(4L)).thenReturn(java.util.Optional.of(project));
        InterviewAiService service = new InterviewAiService(ai, projects, steps, org.mockito.Mockito.mock(dev.swirlit.indezy.repository.UserRepository.class),
            org.mockito.Mockito.mock(dev.swirlit.indezy.service.CompanyHealthService.class));
        org.mockito.Mockito.when(ai.structured(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.contains("x".repeat(InterviewAiService.DEBRIEF_MAX) + "\n</debrief>"), org.mockito.ArgumentMatchers.eq(InterviewAiService.Debrief.class),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(new InterviewAiService.Debrief(InterviewAiService.Outcome.REJECTED, dev.swirlit.indezy.model.enums.LostReason.NO_RESPONSE, "Entretien client", null, null, null))
            .thenReturn(new InterviewAiService.Debrief(null, dev.swirlit.indezy.model.enums.LostReason.RATE_TOO_LOW, "", java.util.List.of("a", "b", "c", "d", "e", "f", "g"), "Merci", "Bonjour"));

        // Turned down: no next round, whatever the AI named; nothing else said, nothing invented.
        InterviewAiService.Debrief rejected = service.debrief(1L, 4L, "x".repeat(InterviewAiService.DEBRIEF_MAX + 50), dev.swirlit.indezy.model.enums.DocumentLanguage.FR);
        assertThat(rejected.outcome()).isEqualTo(InterviewAiService.Outcome.REJECTED);
        assertThat(rejected.nextRound()).isNull();
        // Not answering is no refusal the debrief hears of: another reason, then.
        assertThat(rejected.reason()).isEqualTo(dev.swirlit.indezy.model.enums.LostReason.OTHER);
        assertThat(rejected.nextSteps()).isEmpty();
        assertThat(rejected.thankYouBody()).isEmpty();
        // No outcome said: waiting; six steps at most.
        InterviewAiService.Debrief waiting = service.debrief(1L, 4L, "x".repeat(InterviewAiService.DEBRIEF_MAX + 50), dev.swirlit.indezy.model.enums.DocumentLanguage.FR);
        assertThat(waiting.outcome()).isEqualTo(InterviewAiService.Outcome.WAITING);
        assertThat(waiting.nextSteps()).hasSize(6);
        assertThat(waiting.reason()).isNull();
    }
}
