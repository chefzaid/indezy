package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportLayout;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityReportPdfRendererTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final Instant DECIDED_AT = Instant.parse("2026-12-01T09:42:00Z");

    private final ActivityReportPdfRenderer renderer = new ActivityReportPdfRenderer();

    @Test
    void dailyTable_InFrench_ShouldPrintTheMonthDayByDayWithHolidaysTotalsAndTheElectronicApproval() throws IOException {
        Rendered pdf = render(document(template(ActivityReportLayout.DAILY_TABLE, true, DocumentLanguage.FR),
            ActivityReportStatus.VALIDATED, "Merci, conforme."));

        assertThat(pdf.pageSize().getWidth()).isEqualTo(PDRectangle.A4.getWidth());
        assertThat(pdf.pageSize().getHeight()).isEqualTo(PDRectangle.A4.getHeight());
        assertThat(pdf.pages()).isEqualTo(1);
        assertThat(pdf.text())
            .contains("Compte rendu d'activité", "Novembre 2026", "Validé")
            .contains("Zoé Lefèvre-Dubœuf", "Société Générale", "Capgemini", "Développeur Java")
            .contains("du 01/11/2026 au 30/11/2026")
            .contains("02/11/2026 Lundi 1 Sur site", "06/11/2026 Vendredi 1 Télétravail", "13/11/2026 Vendredi 0,5 Télétravail")
            .contains("Jour férié : Toussaint", "Jour férié : Armistice 1918", "Absence")
            .contains("Jours travaillés : 18,5", "dont 16 sur site et 2,5 en télétravail", "Absences : 1",
                "Jours ouvrés du mois : 20")
            .contains("Commentaire du consultant", "Pont du 10 posé")
            .contains("Validé électroniquement par Jean-Marc Dupont", "le 01/12/2026 à 10:42 (heure de Paris)",
                "Commentaire : Merci, conforme.")
            .contains("Retour avant le 25 à adv@esn.example");
        assertThat(pdf.title()).isEqualTo("Compte rendu d'activité - Novembre 2026");
    }

    @Test
    void calendarGrid_InEnglish_ShouldPrintALandscapeGridWithCodesAndTheClientsTitle() throws IOException {
        ActivityReportTemplate template = new ActivityReportTemplate("Monthly timesheet", ActivityReportLayout.CALENDAR_GRID,
            true, DocumentLanguage.EN, null);

        Rendered pdf = render(document(template, ActivityReportStatus.REJECTED, "The 20th was worked"));

        assertThat(pdf.pageSize().getWidth()).isEqualTo(PDRectangle.A4.getHeight());
        assertThat(pdf.pages()).isEqualTo(1);
        assertThat(pdf.text())
            .contains("Monthly timesheet", "November 2026", "Rejected")
            .contains("Agency / intermediary", "Assignment", "from 01/11/2026 to 30/11/2026")
            .contains("Worked", "Location", "Absence", "O: on site · R: remote · H: public holiday · 0.5: half day")
            .contains("Public holidays: 01/11/2026 All Saints' Day, 11/11/2026 Armistice Day")
            .contains("Days worked: 18.5", "of which 16 on site and 2.5 remote", "Business days in the month: 20")
            .contains("Rejected by Jean-Marc Dupont", "on 01/12/2026 at 10:42 (Paris time)", "Reason: The 20th was worked")
            .doesNotContain("Retour avant");
        assertThat(pdf.title()).isEqualTo("Monthly timesheet - November 2026");
    }

    @Test
    void withoutLocation_ShouldLeaveOutTheOnSiteAndRemoteSplit() throws IOException {
        Rendered table = render(document(template(ActivityReportLayout.DAILY_TABLE, false, DocumentLanguage.FR),
            ActivityReportStatus.DRAFT, null));
        Rendered grid = render(document(template(ActivityReportLayout.CALENDAR_GRID, false, DocumentLanguage.FR),
            ActivityReportStatus.DRAFT, null));

        assertThat(table.text()).contains("Brouillon", "Jours travaillés : 18,5", "Nom, date et signature")
            .doesNotContain("Sur site", "Télétravail", "dont 16", "Lieu", "Validé électroniquement");
        assertThat(grid.text()).doesNotContain("Lieu").contains("Absence");
    }

    @Test
    void submittedReport_ShouldWaitForASignatureWithoutDecision() throws IOException {
        ActivityReportDocument document = new ActivityReportDocument(template(ActivityReportLayout.DAILY_TABLE, true,
            DocumentLanguage.EN), "Zoé", "Acme", null, "Developer", NOVEMBER, List.of(), null,
            ActivityReportStatus.SUBMITTED, null, null, null);

        Rendered pdf = render(document);

        assertThat(pdf.text()).contains("Awaiting approval", "Name, date and signature", "Days worked: 0", "Absences: 0")
            .doesNotContain("Agency / intermediary", "of which", "Consultant's comment");
    }

    @Test
    void unprintableCharactersAndLongTexts_ShouldBeReplacedWrappedAndCutInsteadOfFailing() throws IOException {
        String longWord = "x".repeat(400);
        String comment = "Mission 李 avec un émoji 🚀. " + "Une longue explication du mois. ".repeat(40) + longWord;
        ActivityReportDocument document = new ActivityReportDocument(template(ActivityReportLayout.DAILY_TABLE, true,
            DocumentLanguage.FR), "Consultant 李", "Client", null, "Rôle " + "très long ".repeat(30), NOVEMBER, days(),
            comment, ActivityReportStatus.VALIDATED, "Validateur", DECIDED_AT, "Commentaire ".repeat(40));

        Rendered pdf = render(document);

        assertThat(pdf.pages()).isEqualTo(2);
        assertThat(pdf.text()).contains("Consultant ?", "Mission ? avec un émoji ?", "…").doesNotContain("李");
    }

    @Test
    void template_OfAClient_ShouldFallBackToTheDefaultsForUnsetFields() {
        Client client = new Client();
        client.setActivityReportTitle("   ");
        ActivityReportTemplate defaults = ActivityReportTemplate.of(client);

        assertThat(defaults).isEqualTo(new ActivityReportTemplate(null, ActivityReportLayout.DAILY_TABLE, true,
            DocumentLanguage.FR, null));
        assertThat(ActivityReportTemplate.of(null)).isEqualTo(defaults);

        client.setActivityReportTitle(" Relevé ");
        client.setActivityReportShowsLocation(false);
        client.setActivityReportFooter(" Merci ");
        assertThat(ActivityReportTemplate.of(client)).isEqualTo(new ActivityReportTemplate("Relevé",
            ActivityReportLayout.DAILY_TABLE, false, DocumentLanguage.FR, "Merci"));
    }

    private static ActivityReportTemplate template(ActivityReportLayout layout, boolean showsLocation, DocumentLanguage language) {
        return new ActivityReportTemplate(null, layout, showsLocation, language, "Retour avant le 25 à adv@esn.example");
    }

    private static ActivityReportDocument document(ActivityReportTemplate template, ActivityReportStatus status, String decision) {
        return new ActivityReportDocument(template, "Zoé Lefèvre-Dubœuf", "Société Générale", "Capgemini",
            "Développeur Java / Spring Boot", NOVEMBER, days(), "Pont du 10 posé avec le manager.", status,
            "Jean-Marc Dupont", DECIDED_AT, decision);
    }

    /** Weekdays except the 11th (Armistice): remote on Fridays, a half day on the 13th, an absence on the 20th. */
    private static List<ActivityDay> days() {
        List<ActivityDay> days = new ArrayList<>();
        for (LocalDate date = NOVEMBER.atDay(1); !date.isAfter(NOVEMBER.atEndOfMonth()); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue() || date.getDayOfMonth() == 11) {
                continue;
            }
            ActivityDayType type = date.getDayOfWeek() == DayOfWeek.FRIDAY ? ActivityDayType.REMOTE : ActivityDayType.ONSITE;
            days.add(new ActivityDay(date, date.getDayOfMonth() == 20 ? ActivityDayType.ABSENCE : type, date.getDayOfMonth() == 13));
        }
        return days;
    }

    private Rendered render(ActivityReportDocument document) throws IOException {
        byte[] bytes = renderer.render(document);
        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(pdf).replaceAll("[ \\t]+", " ");
            return new Rendered(text, pdf.getNumberOfPages(), pdf.getPage(0).getMediaBox(),
                pdf.getDocumentInformation().getTitle());
        }
    }

    private record Rendered(String text, int pages, PDRectangle pageSize, String title) {
    }
}
