package dev.swirlit.indezy.service;

import dev.swirlit.indezy.ai.PostingPdfTextTest;
import dev.swirlit.indezy.ai.ProfileImportAiService;
import dev.swirlit.indezy.exception.FieldRejectedException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LinkedInProfilePdfTest {

    /** A line drawn at a column's left edge, in a size. */
    record Drawn(float x, float size, String text) {
    }

    static Drawn side(String text) {
        return new Drawn(30, 10, text);
    }

    static Drawn main(float size, String text) {
        return new Drawn(220, size, text);
    }

    /** Pages laid out as LinkedIn's Save to PDF: the left column drawn first, then the main one. */
    @SafeVarargs
    static byte[] pdf(List<Drawn>... pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (List<Drawn> lines : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    float sideY = 740;
                    float mainY = 740;
                    for (Drawn line : lines) {
                        boolean left = line.x() < 100;
                        float y = left ? sideY : mainY;
                        stream.beginText();
                        stream.setFont(font, line.size());
                        stream.newLineAtOffset(line.x(), y);
                        stream.showText(line.text());
                        stream.endText();
                        float next = y - line.size() * 1.8f;
                        if (left) {
                            sideY = next;
                        } else {
                            mainY = next;
                        }
                    }
                }
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    public static byte[] profile() throws IOException {
        return pdf(List.of(
                side("Contact"), side("alice@example.test"), side("+33 6 12 34 56 78 (Mobile)"), side("www.linkedin.com/in/alice-martin-"),
                side("1a2b3c (LinkedIn)"), side("alice.dev (Portfolio)"), side("github.com/alice (Other)"), side("Top Skills"), side("Kafka"),
                side("Spring Boot"), side("Angular"), side("Languages"), side("English (Full Professional)"), side("Français (Native or Bilingual)"),
                side("Certifications"), side("AWS Certified Solutions Architect"),
                main(26, "Alice Martin"), main(12, "Lead Java freelance | Kafka, Spring"), main(10, "Lyon, Auvergne-Rhône-Alpes, France"),
                main(16, "Summary"), main(10, "Dix ans de Java et de Kafka,"), main(10, "pour la banque et l'énergie."),
                main(16, "Experience"), main(12, "Alice Conseil"), main(11, "Lead Java"), main(10, "January 2024 - Present (2 years 11 months)"),
                main(10, "Lyon"), main(10, "Kafka et Java 21 pour une banque."), main(10, "Page 1 of 2")),
            List.of(main(12, "Banque Exemple"), main(10, "6 years 2 months"), main(11, "Développeuse Java"),
                main(10, "mars 2018 - December 2023 (5 years 10 months)"), main(10, "Spring Boot et Kafka."), main(11, "Stagiaire"),
                main(10, "2017 - 2018 (1 year)"), main(16, "Education"), main(12, "INSA Lyon"),
                main(10, "Diplôme d'ingénieur, Informatique · (2010 - 2015)"), main(12, "Lycée du Parc"), main(10, "(2010)"),
                main(12, "Coursera"), main(10, "Page 2 of 2")));
    }

    @Test
    void readsLinkedInsProfilePdf() throws IOException {
        ProfileImportAiService.Reading reading = LinkedInProfilePdf.read(profile(), LocalDate.of(2026, 11, 23));

        assertThat(reading.firstName()).isEqualTo("Alice");
        assertThat(reading.lastName()).isEqualTo("Martin");
        assertThat(reading.phone()).isEqualTo("+33 6 12 34 56 78");
        assertThat(reading.city()).isEqualTo("Lyon");
        assertThat(reading.position()).isEqualTo("Lead Java");
        assertThat(reading.company()).isEqualTo("Alice Conseil");
        assertThat(reading.bio()).isEqualTo("Dix ans de Java et de Kafka, pour la banque et l'énergie.");
        assertThat(reading.linkedin()).isEqualTo("https://www.linkedin.com/in/alice-martin-1a2b3c");
        assertThat(reading.website()).isEqualTo("https://alice.dev");
        assertThat(reading.github()).isEqualTo("https://github.com/alice");
        assertThat(reading.languages()).containsExactly("Anglais", "Français");
        // Kafka since March 2018 (both positions name it), Spring Boot in the bank's, Angular named nowhere.
        assertThat(reading.skills()).extracting(skill -> skill.name() + ":" + skill.years()).containsExactly("Kafka:8", "Spring Boot:5", "Angular:0");
        assertThat(reading.certifications()).extracting(ProfileImportAiService.Certification::name).containsExactly("AWS Certified Solutions Architect");
        assertThat(reading.positions()).containsExactly(
            new ProfileImportAiService.PastPosition("Alice Conseil", "Lead Java", "2024-01-01", null, "Lyon Kafka et Java 21 pour une banque."),
            new ProfileImportAiService.PastPosition("Banque Exemple", "Développeuse Java", "2018-03-01", "2023-12-01", "Spring Boot et Kafka."),
            new ProfileImportAiService.PastPosition("Banque Exemple", "Stagiaire", "2017-01-01", "2018-01-01", null));
        assertThat(reading.education()).containsExactly(
            new ProfileImportAiService.School("INSA Lyon", "Diplôme d'ingénieur, Informatique", 2010, 2015),
            new ProfileImportAiService.School("Lycée du Parc", null, 0, 2010), new ProfileImportAiService.School("Coursera", null, 0, 0));
    }

    private static LinkedInProfilePdf.Line company(String text) {
        return new LinkedInProfilePdf.Line(text, 12, false);
    }

    private static LinkedInProfilePdf.Line title(String text) {
        return new LinkedInProfilePdf.Line(text, 11, false);
    }

    private static LinkedInProfilePdf.Line text(String text) {
        return new LinkedInProfilePdf.Line(text, 10, false);
    }

    @Test
    void readsThePositionsUnderTheirCompany() {
        List<LinkedInProfileImport.Position> positions = LinkedInProfilePdf.positions(List.of(company("Banque Exemple"), text("6 years 2 months"),
            title("Lead"), text("Jan 2020 - Present (5 years)"), text("Paris"), text("Java"), title("Développeuse"), text("2018 - Dec 2019"),
            company("Autre"), title("Dev"), text("2016 - 2017")));
        assertThat(positions).extracting(position -> position.company() + "/" + position.title() + "/" + position.start() + "/" + position.end())
            .containsExactly("Banque Exemple/Lead/2020-01/null", "Banque Exemple/Développeuse/2018-01/2019-12", "Autre/Dev/2016-01/2017-01");
        assertThat(positions.getFirst().description()).isEqualTo("Paris Java");
        assertThat(positions.get(1).description()).isEmpty();
        assertThat(LinkedInProfilePdf.dates("janvier 2020 - aujourd’hui (4 ans)")).isEqualTo(new LinkedInProfilePdf.Period(YearMonth.of(2020, 1), null));
        assertThat(LinkedInProfilePdf.dates("Lyon - Paris")).isNull();
    }

    @Test
    void refusesWhatIsNotALinkedInProfile() throws IOException {
        byte[] posting = PostingPdfTextTest.pdf(false, "Mission Java Senior", "TJM 650 euros");
        assertThatThrownBy(() -> LinkedInProfilePdf.read(posting, LocalDate.now())).isInstanceOf(FieldRejectedException.class)
            .hasMessageContaining("not a LinkedIn profile");
        assertThatThrownBy(() -> LinkedInProfilePdf.read("%PDF-broken".getBytes(), LocalDate.now())).hasMessageContaining("not a PDF");
        byte[] locked = PostingPdfTextTest.pdf(true, "Secret");
        assertThatThrownBy(() -> LinkedInProfilePdf.read(locked, LocalDate.now())).hasMessageContaining("protected");
    }
}
