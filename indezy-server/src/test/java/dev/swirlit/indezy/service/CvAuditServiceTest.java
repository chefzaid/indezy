package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CvAuditServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 28);

    private static DossierDto dossier(String summary, String city, List<DossierDto.Mission> missions) {
        return new DossierDto("A. M.", "Lead Java", city, List.of(), summary, List.of(new DossierDto.Skill("Java", null, 8)), List.of(), List.of(),
            missions, List.of(), List.of());
    }

    private static DossierDto.Mission mission(String role, LocalDate start, LocalDate end, String description) {
        return new DossierDto.Mission(role, null, start, end, "Java", description);
    }

    @Test
    void findsTheNamesAnAnonymizedDossierStillGivesWhateverTheirCase() {
        DossierDto dossier = dossier("Dix ans chez la SNCF et chez Banque Éxemple.", "Lyon", List.of(
            mission("Lead Java", LocalDate.of(2025, 1, 1), null, "Migration de 40 services avec Julie Durand.")));

        CvAuditService.Audit audit = CvAuditService.audit(dossier, DocumentLanguage.FR, List.of("SNCF", "banque exemple", "Julie Durand", "Alice Corp"),
            TODAY, TODAY);

        assertThat(audit.leaks()).isTrue();
        assertThat(audit.findings()).filteredOn(finding -> finding.check() == CvAuditService.Check.LEAK)
            .extracting(CvAuditService.Finding::detail).containsExactly("SNCF", "banque exemple", "Julie Durand");
        // A name inside another word is not one: "SNCFR" does not leak "SNCF".
        assertThat(CvAuditService.audit(dossier("Chez SNCFR.", "Lyon", List.of()), DocumentLanguage.FR, List.of("SNCF"), TODAY, TODAY).leaks()).isFalse();
    }

    @Test
    void pointsOutWeakPhrasingMissingFiguresGapsAndTheOtherLanguage() {
        DossierDto dossier = dossier("Ten years of Java and Kafka for the banks and the insurers.", null, List.of(
            mission("Lead Java", LocalDate.of(2025, 6, 1), null, "J'ai participé à la migration du SI de paie."),
            mission("Dev Java", LocalDate.of(2023, 1, 1), LocalDate.of(2024, 12, 31), "Migration de 40 services vers Kafka."),
            mission("Dev", LocalDate.of(2022, 1, 1), LocalDate.of(2022, 6, 30), null)));

        CvAuditService.Audit audit = CvAuditService.audit(dossier, DocumentLanguage.FR, List.of(), null, TODAY);

        assertThat(audit.findings()).extracting(CvAuditService.Finding::check).contains(CvAuditService.Check.WEAK_PHRASING,
            CvAuditService.Check.OTHER_LANGUAGE, CvAuditService.Check.NO_FIGURE, CvAuditService.Check.NO_DESCRIPTION,
            CvAuditService.Check.DATE_GAP, CvAuditService.Check.MISSING_CITY, CvAuditService.Check.MISSING_AVAILABILITY)
            .doesNotContain(CvAuditService.Check.LEAK, CvAuditService.Check.MISSING_SUMMARY, CvAuditService.Check.LONG);
        assertThat(audit.findings()).filteredOn(finding -> finding.check() == CvAuditService.Check.DATE_GAP)
            .extracting(CvAuditService.Finding::detail).containsExactly("2022-06-30 – 2023-01-01", "2024-12-31 – 2025-06-01");
        assertThat(audit.findings()).filteredOn(finding -> finding.check() == CvAuditService.Check.WEAK_PHRASING).singleElement()
            .satisfies(finding -> assertThat(finding.block()).isEqualTo("Lead Java"));
        // The most serious first.
        assertThat(audit.findings().getFirst().severity()).isEqualTo(CvAuditService.Severity.WARNING);
        assertThat(audit.pages()).isEqualTo(1);
    }

    @Test
    void saysWhenTheDossierRunsLongOrMissesItsSummary() {
        String longText = "Migration de services vers Kafka avec 40 équipes. ".repeat(220);
        CvAuditService.Audit audit = CvAuditService.audit(dossier(null, "Lyon", List.of(mission("Lead", LocalDate.of(2025, 1, 1), null, longText))),
            DocumentLanguage.FR, List.of(), TODAY, TODAY);
        assertThat(audit.pages()).isGreaterThan(CvAuditService.PAGES_MAX);
        assertThat(audit.findings()).extracting(CvAuditService.Finding::check).contains(CvAuditService.Check.LONG, CvAuditService.Check.MISSING_SUMMARY);
    }
}
