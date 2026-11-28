package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.dto.CareerDtos;
import com.anthropic.models.messages.OutputConfig;
import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.CareerService;
import dev.swirlit.indezy.service.DossierService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvRewriteAiServiceTest {

    private static DossierDto dossier() {
        return new DossierDto("A. M.", "Développeuse Java", "Lyon", List.of(), "Dix ans de Java.",
            List.of(new DossierDto.Skill("Java", null, 10), new DossierDto.Skill("Kafka", null, 3), new DossierDto.Skill("Angular", null, 2)),
            List.of("Anglais"), List.of("AWS Solutions Architect"),
            List.of(new DossierDto.Mission("Lead Java", null, LocalDate.of(2025, 1, 6), null, "Java, Kafka", "Migration vers Kafka."),
                new DossierDto.Mission("Dev Java", null, LocalDate.of(2023, 3, 1), LocalDate.of(2024, 12, 20), "Java", null)),
            List.of(new DossierDto.Mission("Développeuse", null, LocalDate.of(2015, 9, 1), LocalDate.of(2022, 12, 31), "Java", "Paie en Java.")),
            List.of());
    }

    private static final List<CareerDtos.Experience> EXPERIENCES = List.of(new CareerDtos.Experience(70L, "Banque X", "Développeuse",
        LocalDate.of(2015, 9, 1), LocalDate.of(2022, 12, 31), "Paie en Java.", "Java", false));

    @Test
    void tellsTheAiThePositioningAndStyleButNoNameNorClient() {
        String context = CvRewriteAiService.context(dossier(), " Staff Java / Kafka ", CvRewriteAiService.Style.TECHNICAL, DocumentLanguage.EN);

        assertThat(context).startsWith("Answer in English.").contains("<positioning>Staff Java / Kafka</positioning>", "technical depth",
            "Past missions:\n1. Lead Java (2025-01-06 to now), stack: Java, Kafka", "Past positions:\n1. Développeuse (2015-09-01 to 2022-12-31)",
            "Description: none").doesNotContain("Banque X", "A. M.");
        assertThat(CvRewriteAiService.context(dossier(), "", null, DocumentLanguage.FR)).contains("<positioning>as the CV stands</positioning>",
            "impact");
    }

    @Test
    void keepsOnlyWhatTheAiMayChangeEachBlockBeforeAndAfter() {
        CvRewriteAiService.Rewriting rewriting = new CvRewriteAiService.Rewriting(" Lead Java / Kafka ", "Experte Java et Kafka.",
            List.of("kafka", "Rust", "java"), List.of(new CvRewriteAiService.Text(1, "Migration du SI vers Kafka."),
                new CvRewriteAiService.Text(2, "Inventée"), new CvRewriteAiService.Text(1, "Doublon")),
            List.of(new CvRewriteAiService.Text(1, "Logiciel de paie en Java.")));

        CvRewriteAiService.CvRewrite rewrite = CvRewriteAiService.review(dossier(), EXPERIENCES, rewriting);

        assertThat(rewrite.headline()).isEqualTo(new CvRewriteAiService.Block("Développeuse Java", "Lead Java / Kafka"));
        assertThat(rewrite.summary()).isEqualTo(new CvRewriteAiService.Block("Dix ans de Java.", "Experte Java et Kafka."));
        // Rust is not in the profile: dropped; Angular, not named, follows.
        assertThat(rewrite.skillsBefore()).containsExactly("Java", "Kafka", "Angular");
        assertThat(rewrite.skills()).containsExactly("Kafka", "Java", "Angular");
        // A mission without a description gets none; the first text for a number wins.
        assertThat(rewrite.missions()).extracting(CvRewriteAiService.Item::after).containsExactly("Migration du SI vers Kafka.", null);
        assertThat(rewrite.positions()).singleElement().isEqualTo(new CvRewriteAiService.Item(1, 70L, "Développeuse", "Paie en Java.",
            "Logiciel de paie en Java."));
    }

    @Test
    void makesNothingUpForAnEmptyProfileAndKeepsAnUnwrittenSummaryEmpty() {
        DossierDto empty = new DossierDto("A. M.", null, null, List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        CvRewriteAiService.CvRewrite rewrite = CvRewriteAiService.review(empty, List.of(),
            new CvRewriteAiService.Rewriting("Expert", "Un résumé inventé", List.of(), List.of(), List.of()));

        assertThat(rewrite.headline().after()).isNull();
        assertThat(rewrite.summary().after()).isNull();
    }

    @Test
    void rendersTheReviewedWordingKeepingTheFacts() {
        DossierDto applied = CvRewriteAiService.apply(dossier(), new CvRewriteAiService.Reviewed("Lead Java", "x".repeat(950),
            List.of("Angular"), List.of(new CvRewriteAiService.Text(2, "Nouveau texte.")), List.of(new CvRewriteAiService.Text(1, "Paie."))));

        assertThat(applied.position()).isEqualTo("Lead Java");
        assertThat(applied.summary()).hasSizeLessThanOrEqualTo(DossierAiService.SUMMARY_MAX + 1).endsWith("…");
        assertThat(applied.skills()).extracting(DossierDto.Skill::name).containsExactly("Angular", "Java", "Kafka");
        assertThat(applied.missions().get(0).description()).isEqualTo("Migration vers Kafka.");
        assertThat(applied.missions().get(1)).satisfies(mission -> {
            assertThat(mission.description()).isEqualTo("Nouveau texte.");
            assertThat(mission.startsOn()).isEqualTo(LocalDate.of(2023, 3, 1));
        });
        assertThat(applied.positions().getFirst().description()).isEqualTo("Paie.");
        // Nothing reviewed: the dossier as it is.
        assertThat(CvRewriteAiService.apply(dossier(), new CvRewriteAiService.Reviewed(" ", null, null, null, null))).isEqualTo(dossier());
    }

    @Test
    void writesTheApprovedWordingToTheProfileOnlyForItsOwnPositions() {
        UserRepository users = mock(UserRepository.class);
        CareerService career = mock(CareerService.class);
        User user = new User();
        user.setPosition("Développeuse Java");
        user.setBio("Dix ans de Java.");
        when(users.findById(3L)).thenReturn(Optional.of(user));
        when(career.experiences(9L)).thenReturn(EXPERIENCES);
        CvRewriteAiService service = new CvRewriteAiService(mock(AiService.class), mock(DossierService.class), career, users);

        service.approve(3L, 9L, new CvRewriteAiService.Approved(" Lead Java ", null, List.of(new CvRewriteAiService.PositionText(70L, " Paie. "),
            new CvRewriteAiService.PositionText(99L, "Pas à elle"), new CvRewriteAiService.PositionText(70L, " "))));

        assertThat(user.getPosition()).isEqualTo("Lead Java");
        assertThat(user.getBio()).isEqualTo("Dix ans de Java.");
        verify(career).updateExperience(eq(9L), eq(70L), eq(new CareerDtos.ExperienceDetails("Banque X", "Développeuse",
            LocalDate.of(2015, 9, 1), LocalDate.of(2022, 12, 31), "Paie.", "Java", false)));
        verify(career, never()).updateExperience(eq(9L), eq(99L), any());
    }

    @Test
    void asksForOneRefinementKeepingTheLockedPhrasesThatAreInTheBlock() {
        String context = CvRewriteAiService.refineContext("Migration vers Kafka.", CvRewriteAiService.Refinement.HIGHLIGHT, " Kubernetes ",
            List.of("Kafka"), DocumentLanguage.EN);
        assertThat(context).startsWith("Answer in English.\nBring out Kubernetes, only where the block states it.")
            .contains("<block>\nMigration vers Kafka.\n</block>", "<locked>\n- Kafka\n</locked>");
        assertThat(CvRewriteAiService.refineContext("Texte.", CvRewriteAiService.Refinement.TRANSLATE, null, List.of(), DocumentLanguage.FR))
            .contains("Translate it into French").doesNotContain("<locked>");
    }

    @Test
    void refusesARefinementThatChangedALockedPhrase() {
        AiService ai = mock(AiService.class);
        CvRewriteAiService service = new CvRewriteAiService(ai, mock(DossierService.class), mock(CareerService.class), mock(UserRepository.class));
        when(ai.structured(eq(3L), eq(AiFeature.DOSSIER), anyString(), anyString(), eq(CvRewriteAiService.Refined.class), anyLong(),
            any(OutputConfig.Effort.class))).thenReturn(new CvRewriteAiService.Refined(" Migration du SI vers Kafka. "));

        // A phrase the block does not hold is not asked for.
        assertThat(service.refine(3L, "Migration vers Kafka.", CvRewriteAiService.Refinement.SHORTER, null, List.of("Kafka", "Rust", " "),
            DocumentLanguage.FR)).isEqualTo("Migration du SI vers Kafka.");
        assertThatThrownBy(() -> service.refine(3L, "Migration vers Kafka.", CvRewriteAiService.Refinement.SHORTER, null,
            List.of("Migration vers"), DocumentLanguage.FR)).isInstanceOf(InvalidStateException.class).hasMessageContaining("locked phrase");
    }
}
