package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.dto.SkillDetailDto;
import dev.swirlit.indezy.dto.UserDto;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierLayout;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.SkillLevel;
import dev.swirlit.indezy.pdf.DossierPdfRenderer;
import dev.swirlit.indezy.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DossierServiceTest {

    private final UserService users = mock(UserService.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final TrainingService trainings = mock(TrainingService.class);
    private final DossierPdfRenderer renderer = mock(DossierPdfRenderer.class);
    private final DossierService service = new DossierService(users, projects, trainings, renderer,
        Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC));
    private final UserDto profile = new UserDto();

    private static Project project(String role, ProjectStatus status, LocalDate start, String client) {
        Project project = new Project();
        project.setRole(role);
        project.setStatus(status);
        project.setStartDate(start);
        project.setTechStack(" ");
        if (client != null) {
            Client company = new Client();
            company.setCompanyName(client);
            project.setClient(company);
        }
        return project;
    }

    @BeforeEach
    void setUp() {
        profile.setFirstName("Élodie");
        profile.setLastName("Martin");
        profile.setEmail("elodie@example.com");
        profile.setPhone(" ");
        profile.setPosition("Lead Java");
        profile.setBio("Quinze ans de Java.");
        profile.setSkills(List.of("Java", "Kafka"));
        profile.setSkillDetails(Map.of("Java", new SkillDetailDto(SkillLevel.EXPERT, 15)));
        profile.setLanguages(List.of("Français"));
        when(users.getUserProfile(1L)).thenReturn(profile);
        when(trainings.validCertifications(5L)).thenReturn(List.of("CKA"));
        when(projects.findByFreelanceId(5L)).thenReturn(List.of(
            project("Audit", ProjectStatus.WON, LocalDate.of(2024, 1, 8), "Banque"),
            project("Contact", ProjectStatus.CONTACT, LocalDate.of(2026, 1, 1), "Autre"),
            project("Lead", ProjectStatus.WON, LocalDate.of(2025, 3, 1), "Assurances"),
            project("Sans date", ProjectStatus.WON, null, null)));
    }

    @Test
    void namesTheAuthorAndTheClientsOfTheWonMissionsLatestFirst() {
        DossierDto dossier = service.dossier(1L, 5L, DossierLayout.NAMED);

        assertThat(dossier.name()).isEqualTo("Élodie Martin");
        assertThat(dossier.contact()).containsExactly("elodie@example.com");
        assertThat(dossier.skills()).containsExactly(new DossierDto.Skill("Java", SkillLevel.EXPERT, 15), new DossierDto.Skill("Kafka", null, null));
        assertThat(dossier.certifications()).containsExactly("CKA");
        assertThat(dossier.missions()).extracting(DossierDto.Mission::role).containsExactly("Lead", "Audit", "Sans date");
        assertThat(dossier.missions().get(0).client()).isEqualTo("Assurances");
        assertThat(dossier.missions().get(0).stack()).isNull();
    }

    @Test
    void anonymizesWithInitialsNoContactAndNoClient() {
        DossierDto dossier = service.dossier(1L, 5L, DossierLayout.ANONYMOUS);

        assertThat(dossier.name()).isEqualTo("É. M.");
        assertThat(dossier.contact()).isEmpty();
        assertThat(dossier.missions()).allSatisfy(mission -> assertThat(mission.client()).isNull());
    }

    @Test
    void namesTheFileAfterTheDossierInItsLanguage() {
        when(renderer.render(any(), eq(DocumentLanguage.EN), eq(LocalDate.of(2026, 11, 14)))).thenReturn(new byte[] {1});
        when(renderer.render(any(), eq(DocumentLanguage.FR), any())).thenReturn(new byte[] {2});
        assertThat(service.pdf(1L, 5L, DossierLayout.NAMED, DocumentLanguage.EN).fileName()).isEqualTo("skills-profile-elodie-martin.pdf");
        assertThat(service.pdf(1L, 5L, DossierLayout.ANONYMOUS, DocumentLanguage.FR).fileName()).isEqualTo("dossier-competences-e-m.pdf");
    }

    @Test
    void cutsALongDescriptionAtAWordAndNamesAFileWithoutAName() {
        assertThat(DossierService.excerpt(null)).isNull();
        assertThat(DossierService.excerpt("Court.")).isEqualTo("Court.");
        String cut = DossierService.excerpt("mot ".repeat(200));
        assertThat(cut).endsWith("mot…").hasSizeLessThanOrEqualTo(DossierService.DESCRIPTION_LENGTH + 1);
        assertThat(DossierService.excerpt("x".repeat(700))).hasSize(DossierService.DESCRIPTION_LENGTH + 1);
        assertThat(DossierService.fileWord("")).isEqualTo("profile");
        UserDto nameless = new UserDto();
        assertThat(DossierService.initials(nameless)).isEmpty();
    }
}
