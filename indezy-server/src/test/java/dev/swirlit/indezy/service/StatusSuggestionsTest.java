package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class StatusSuggestionsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 22);

    private static Project project(long id, ProjectStatus status) {
        Project project = new Project();
        project.setId(id);
        project.setRole("Lead Java " + id);
        project.setStatus(status);
        Client client = new Client();
        client.setCompanyName("Conseil X");
        project.setClient(client);
        return project;
    }

    private static ContactInteraction said(long id, Project project, int daysAgo, InteractionDirection direction, String summary) {
        ContactInteraction interaction = new ContactInteraction();
        interaction.setId(id);
        interaction.setProject(project);
        interaction.setOccurredOn(TODAY.minusDays(daysAgo));
        interaction.setDirection(direction);
        interaction.setSummary(summary);
        return interaction;
    }

    @Test
    void readsARefusalOrAnInterviewInviteFromWhatContactsSaid() {
        Project refused = project(1, ProjectStatus.INTERVIEW);
        Project invited = project(2, ProjectStatus.CONTACT);
        Project already = project(3, ProjectStatus.LOST);
        Project changedSince = project(4, ProjectStatus.CONTACT);
        changedSince.setUpdatedAt(LocalDateTime.of(2026, 11, 21, 9, 0));
        Project interviewing = project(5, ProjectStatus.INTERVIEW);

        List<DashboardStatsDto.StatusSuggestion> suggestions = StatusSuggestions.build(List.of(
            said(10, refused, 2, InteractionDirection.INCOMING, "Malheureusement, le client a retenu un autre profil."),
            said(11, refused, 1, InteractionDirection.INCOMING, "Merci pour votre retour"),
            said(12, invited, 3, InteractionDirection.INCOMING, "Pouvez-vous me donner vos disponibilités pour un entretien ?"),
            said(13, already, 1, InteractionDirection.INCOMING, "Profil non retenu"),
            said(14, changedSince, 5, InteractionDirection.INCOMING, "Un entretien jeudi ?"),
            said(15, interviewing, 1, InteractionDirection.INCOMING, "Second entretien lundi"),
            said(16, invited, 1, InteractionDirection.OUTGOING, "Not selected"),
            said(17, refused, 30, InteractionDirection.INCOMING, "Not moving forward")), TODAY);

        // One per opportunity, from its latest telling exchange; what was answered before the last change, or sent, says nothing.
        assertThat(suggestions).extracting(DashboardStatsDto.StatusSuggestion::getProjectId, DashboardStatsDto.StatusSuggestion::getSuggested,
            DashboardStatsDto.StatusSuggestion::getInteractionId).containsExactly(tuple(1L, ProjectStatus.LOST, 10L), tuple(2L, ProjectStatus.INTERVIEW, 12L));
        assertThat(suggestions.getFirst().getClientName()).isEqualTo("Conseil X");
    }

    @Test
    void readsTheUsualWordsInFrenchAndEnglish() {
        assertThat(StatusSuggestions.suggest("Votre candidature n'a pas été retenue", ProjectStatus.OFFER)).isEqualTo(ProjectStatus.LOST);
        assertThat(StatusSuggestions.suggest("We decided not to move forward. Unfortunately…", ProjectStatus.CONTACT)).isEqualTo(ProjectStatus.LOST);
        assertThat(StatusSuggestions.suggest("Le poste est pourvu.", ProjectStatus.INTERVIEW)).isEqualTo(ProjectStatus.LOST);
        assertThat(StatusSuggestions.suggest("Can we schedule an interview?", ProjectStatus.CONTACT)).isEqualTo(ProjectStatus.INTERVIEW);
        assertThat(StatusSuggestions.suggest("Rendez-vous en visio mardi", ProjectStatus.CONTACT)).isEqualTo(ProjectStatus.INTERVIEW);
        assertThat(StatusSuggestions.suggest("Merci, je reviens vers vous", ProjectStatus.CONTACT)).isNull();
        assertThat(StatusSuggestions.suggest("Entretien technique demain", ProjectStatus.OFFER)).isNull();
    }
}
