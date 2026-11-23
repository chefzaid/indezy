package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.SkillLevel;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DossierPdfRendererTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private final DossierPdfRenderer renderer = new DossierPdfRenderer();

    private static DossierDto dossier(String client, List<DossierDto.Mission> missions) {
        return new DossierDto("Alice Martin", "Lead Java", "Paris", List.of("alice@example.com", "06 12 34 56 78"),
            "Quinze ans de Java et de Kafka.", List.of(new DossierDto.Skill("Java", SkillLevel.EXPERT, 15),
            new DossierDto.Skill("Kafka", null, null)), List.of("Français", "Anglais"), List.of("CKA"),
            missions == null ? List.of(new DossierDto.Mission("Lead Java", client, LocalDate.of(2025, 3, 1), LocalDate.of(2026, 2, 28),
                "Java 21, Kafka", "Refonte du SI de paiement."), new DossierDto.Mission("Architecte", client, LocalDate.of(2026, 3, 2), null,
                null, null)) : missions, List.of(), List.of());
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void writesTheProfileSkillsCertificationsAndMissionsInFrench() throws IOException {
        String text = text(renderer.render(dossier("Banque Exemple", null), DocumentLanguage.FR, TODAY));

        assertThat(text).contains("Alice Martin", "Dossier de compétences", "Lead Java · Paris", "alice@example.com",
            "Java (expert, 15 an(s)) · Kafka", "Français, Anglais", "CKA", "Lead Java · Banque Exemple", "Mars 2025 – février 2026",
            "depuis Mars 2026", "Environnement : Java 21, Kafka", "Généré par Indezy le 14 novembre 2026");
    }

    @Test
    void hidesTheClientsInEnglishAndSaysWhenNoMissionIsRecorded() throws IOException {
        String text = text(renderer.render(dossier(null, null), DocumentLanguage.EN, TODAY));
        assertThat(text).contains("Skills profile", "Client disclosed on request", "Environment: Java 21, Kafka");

        DossierDto empty = new DossierDto("A. M.", null, null, List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThat(text(renderer.render(empty, DocumentLanguage.EN, TODAY))).contains("No signed mission recorded.")
            .doesNotContain("Summary", "Languages", "Education");
    }

    @Test
    void printsThePastPositionsAndSchoolsAfterTheMissions() throws IOException {
        DossierDto.Mission bank = new DossierDto.Mission("Développeuse Java", "Banque Exemple", LocalDate.of(2015, 9, 1),
            LocalDate.of(2019, 6, 30), "Java 8", "Paie.");
        DossierDto withHistory = new DossierDto("Alice Martin", null, null, List.of(), null, List.of(), List.of(), List.of(),
            dossier("Banque", null).missions(), List.of(bank), List.of(new DossierDto.Education("INSA Lyon", "Ingénieure", 2010, 2015),
            new DossierDto.Education("Lycée du Parc", null, null, 2010)));
        String text = text(renderer.render(withHistory, DocumentLanguage.FR, TODAY));
        assertThat(text).contains("Expériences antérieures", "Développeuse Java · Banque Exemple", "Septembre 2015 – juin 2019", "Formation",
            "Ingénieure · INSA Lyon (2010 – 2015)", "Lycée du Parc (2010)");
        assertThat(text.indexOf("Expériences antérieures")).isGreaterThan(text.indexOf("Lead Java · Banque"));

        // Without a won mission, the past positions are the experience, with no word of missing missions.
        DossierDto onlyHistory = new DossierDto("A. M.", null, null, List.of(), null, List.of(), List.of(), List.of(), List.of(),
            List.of(bank), List.of());
        assertThat(text(renderer.render(onlyHistory, DocumentLanguage.EN, TODAY))).contains("Experience", "Développeuse Java")
            .doesNotContain("No signed mission", "Earlier experience");
    }

    @Test
    void goesOnToAnotherPageForManyMissions() throws IOException {
        List<DossierDto.Mission> missions = IntStream.range(0, 40).mapToObj(index -> new DossierDto.Mission("Mission " + index, "Client",
            LocalDate.of(2020, 1, 1), LocalDate.of(2020, 6, 30), "Java", "Une mission longue décrite sur plusieurs lignes. ".repeat(4)))
            .toList();
        try (PDDocument document = Loader.loadPDF(renderer.render(dossier("Client", missions), DocumentLanguage.FR, TODAY))) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(document.getDocumentInformation().getTitle()).isEqualTo("Dossier de compétences – Alice Martin");
            assertThat(new PDFTextStripper().getText(document)).contains("Mission 39");
        }
    }
}
