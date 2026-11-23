package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DossierAiServiceTest {

    private static DossierDto dossier(String summary, List<DossierDto.Mission> missions) {
        return new DossierDto("A. M.", "Développeuse Java", "Lyon", List.of(), summary,
            List.of(new DossierDto.Skill("Java", null, 8), new DossierDto.Skill("Kafka", null, 3), new DossierDto.Skill("Angular", null, null)),
            List.of("Anglais"), List.of("AWS"), missions, List.of(), List.of());
    }

    private static DossierDto.Mission mission(String role, String description) {
        return new DossierDto.Mission(role, "Banque X", LocalDate.of(2025, 1, 1), null, "Java", description);
    }

    @Test
    void keepsOnlyWhatTheProfileSays() {
        DossierDto base = dossier("Dix ans de Java.", List.of(mission("Lead Java", "Paie."), mission("Dev Java", null)));
        DossierDto tailored = DossierAiService.apply(base, new DossierAiService.Tailoring(" Java et Kafka pour la paie. ",
            List.of("kafka", "Cobol", "Kafka", " Java "),
            List.of(new DossierAiService.MissionText(1, "Paie sur Kafka."), new DossierAiService.MissionText(2, "Inventée"),
                new DossierAiService.MissionText(7, "Hors liste"))));

        assertThat(tailored.summary()).isEqualTo("Java et Kafka pour la paie.");
        assertThat(tailored.skills()).extracting(DossierDto.Skill::name).containsExactly("Kafka", "Java", "Angular");
        assertThat(tailored.skills().getFirst().years()).isEqualTo(3);
        assertThat(tailored.missions()).extracting(DossierDto.Mission::description).containsExactly("Paie sur Kafka.", null);
        assertThat(tailored.missions().getFirst().client()).isEqualTo("Banque X");
        assertThat(tailored.name()).isEqualTo("A. M.");
        assertThat(tailored.certifications()).containsExactly("AWS");
    }

    @Test
    void keepsTheDossierWhenTheAnswerIsBlankOrTheProfileEmpty() {
        DossierDto base = dossier(null, List.of(mission("Lead Java", "Paie.")));
        DossierDto blank = DossierAiService.apply(base, new DossierAiService.Tailoring("  ", null, null));
        assertThat(blank.summary()).isNull();
        assertThat(blank.skills()).extracting(DossierDto.Skill::name).containsExactly("Java", "Kafka", "Angular");
        assertThat(blank.missions().getFirst().description()).isEqualTo("Paie.");

        DossierDto empty = new DossierDto("A. M.", null, null, List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(DossierAiService.apply(empty, new DossierAiService.Tailoring("Experte reconnue.", List.of("Java"), List.of())).summary())
            .isNull();

        String longSummary = "mot ".repeat(400);
        assertThat(DossierAiService.apply(base, new DossierAiService.Tailoring(longSummary, null, null)).summary())
            .hasSizeLessThanOrEqualTo(DossierAiService.SUMMARY_MAX + 1).endsWith("…");
    }

    @Test
    void sendsNeitherTheNameNorTheClients() {
        Project project = new Project();
        project.setRole("Lead Kafka");
        project.setTechStack("Kafka");
        project.setDescription("Flux temps réel.");
        String context = DossierAiService.context(project, dossier("Dix ans de Java.", List.of(mission("Lead Java", "Paie."))), DocumentLanguage.EN);
        assertThat(context).contains("Answer in English", "Lead Kafka", "Flux temps réel.", "1. Lead Java (2025-01-01 to now), stack: Java",
            "Description: Paie.", "- Java, 8 years", "Certifications: AWS").doesNotContain("A. M.", "Banque X");
    }
}
