package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.CvProfileDtos;
import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvProfileServiceTest {

    private static final DossierDto DOSSIER = new DossierDto("Alice Martin", "Développeuse Java", "Lyon", List.of(), "Dix ans de Java.",
        List.of(new DossierDto.Skill("Java", null, 10), new DossierDto.Skill("Kafka", null, 3), new DossierDto.Skill("Angular", null, 2)),
        List.of(), List.of(), List.of(new DossierDto.Mission("Lead Java", "Banque X", LocalDate.of(2025, 1, 6), null, "Java, Kafka", "Paie."),
            new DossierDto.Mission("Dev Angular", "Assureur", LocalDate.of(2023, 3, 1), LocalDate.of(2024, 12, 20), "Angular", "Front.")),
        List.of(new DossierDto.Mission("Développeuse", "Éditeur", LocalDate.of(2015, 9, 1), LocalDate.of(2022, 12, 31), "Java", "Paie en Java.")),
        List.of());

    private static CvProfileDtos.Profile profile(String headline, List<String> skills, List<CvProfileDtos.Item> missions,
                                                 List<CvProfileDtos.Item> positions) {
        return new CvProfileDtos.Profile(1L, "Backend", true, DocumentLanguage.FR, headline, null, skills, missions, positions, null, null, List.of());
    }

    @Test
    void drawsTheDossierWithTheProfilesChoicesAndTheProfilesFacts() {
        DossierDto shaped = CvProfileService.shape(DOSSIER, List.of(10L, 11L), List.of(20L), profile(" Lead backend Java ",
            List.of("kafka", "Java", "Rust"), List.of(new CvProfileDtos.Item(10L, " Paie migrée vers Kafka. "), new CvProfileDtos.Item(99L, "Partie")),
            List.of()));

        assertThat(shaped.position()).isEqualTo("Lead backend Java");
        assertThat(shaped.summary()).isEqualTo("Dix ans de Java.");
        // Rust is no longer in the profile, Angular is not chosen; the Angular mission and the position are left out.
        assertThat(shaped.skills()).extracting(DossierDto.Skill::name).containsExactly("Kafka", "Java");
        assertThat(shaped.missions()).singleElement().satisfies(mission -> {
            assertThat(mission.description()).isEqualTo("Paie migrée vers Kafka.");
            assertThat(mission.client()).isEqualTo("Banque X");
            assertThat(mission.startsOn()).isEqualTo(LocalDate.of(2025, 1, 6));
        });
        assertThat(shaped.positions()).isEmpty();
    }

    @Test
    void keepsWhatAProfileLeavesUnchosenAsTheProfileSays() {
        assertThat(CvProfileService.shape(DOSSIER, List.of(10L, 11L), List.of(20L), profile(" ", null, null, null))).isEqualTo(DOSSIER);
        // In the profile's order, a mission without its own wording as it is.
        assertThat(CvProfileService.shape(DOSSIER, List.of(10L, 11L), List.of(20L), profile(null, null,
            List.of(new CvProfileDtos.Item(11L, null), new CvProfileDtos.Item(10L, " ")), null)).missions())
            .extracting(DossierDto.Mission::role).containsExactly("Dev Angular", "Lead Java");
    }

    @Test
    void writesATwinsItemsInTheSourcesOrderWithTheirTranslatedWording() {
        java.util.Map<String, String> translated = java.util.Map.of("mission-10", "Payroll flows.", "position-20", "Payroll in Java.");
        assertThat(CvProfileService.twinItems("mission", null, List.of(10L, 11L), translated))
            .containsExactly(new CvProfileDtos.Item(10L, "Payroll flows."), new CvProfileDtos.Item(11L, null));
        assertThat(CvProfileService.twinItems("mission", List.of(new CvProfileDtos.Item(11L, "Front."), new CvProfileDtos.Item(10L, null)),
            List.of(10L, 11L), translated)).containsExactly(new CvProfileDtos.Item(11L, null), new CvProfileDtos.Item(10L, "Payroll flows."));
    }

    @Test
    void flagsTheChangesInTheFactsAProfileReliesOn() {
        CvProfileDtos.Facts was = new CvProfileDtos.Facts(List.of("Java", "Kafka"), List.of("CKA"), java.util.Map.of(
            "mission-10", new CvProfileDtos.ItemFacts("Lead Kafka", "2025-03-01", null, "Kafka"),
            "mission-11", new CvProfileDtos.ItemFacts("Dev Angular", "2023-03-01", "2024-12-20", "Angular"),
            "position-20", new CvProfileDtos.ItemFacts("Développeuse", "2015-09-01", "2022-12-31", "Java")));
        CvProfileDtos.Facts now = new CvProfileDtos.Facts(List.of("Java", "Rust"), List.of("CKA", "AWS SA"), java.util.Map.of(
            "mission-10", new CvProfileDtos.ItemFacts("Lead Kafka", "2025-03-01", "2026-10-31", "Kafka, Flink"),
            "mission-11", new CvProfileDtos.ItemFacts("Dev Angular", "2023-03-01", "2024-12-20", "Angular"),
            "mission-12", new CvProfileDtos.ItemFacts("Architecte", "2026-11-02", null, "AWS")));
        CvProfileDtos.Profile profile = profile(null, List.of("Kafka", "Java"), List.of(new CvProfileDtos.Item(10L, "Flux de paie."),
            new CvProfileDtos.Item(11L, null)), List.of(new CvProfileDtos.Item(20L, "Paie.")));

        assertThat(CvProfileService.changes(was, now, profile)).containsExactly(
            new CvProfileDtos.Change(CvProfileDtos.ChangeKind.SKILL_REMOVED, "Kafka", null),
            new CvProfileDtos.Change(CvProfileDtos.ChangeKind.SKILL_ADDED, "Rust", null),
            new CvProfileDtos.Change(CvProfileDtos.ChangeKind.CERTIFICATION_ADDED, "AWS SA", null),
            new CvProfileDtos.Change(CvProfileDtos.ChangeKind.ITEM_CHANGED, "Lead Kafka", List.of("dates", "stack")),
            new CvProfileDtos.Change(CvProfileDtos.ChangeKind.ITEM_ADDED, "Architecte", null),
            new CvProfileDtos.Change(CvProfileDtos.ChangeKind.ITEM_REMOVED, "Développeuse", null));
        // A profile showing all as the profile says relies on nothing but the certifications.
        assertThat(CvProfileService.changes(was, now, profile(null, null, null, null)))
            .containsExactly(new CvProfileDtos.Change(CvProfileDtos.ChangeKind.CERTIFICATION_ADDED, "AWS SA", null));
        assertThat(CvProfileService.changes(was, was, profile)).isEmpty();
    }
}
