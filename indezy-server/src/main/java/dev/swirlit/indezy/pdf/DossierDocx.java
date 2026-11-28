package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A dossier de compétences as an editable Word document, laid out as its PDF ({@link DossierPage}): who, the summary,
 * the skills, languages and certifications, the missions, the past positions and the education. French ESNs paste it
 * into their own template, so it keeps to plain paragraphs and headings.
 */
final class DossierDocx {

    private static final int HEADING = 25;
    private static final int SUBTITLE = 22;
    private static final String GREY = "555555";

    private DossierDocx() {
    }

    static byte[] render(DossierDto dossier, DocumentLanguage language, LocalDate today) {
        return render(dossier, language, today, DossierLook.CLASSIC);
    }

    static byte[] render(DossierDto dossier, DocumentLanguage language, LocalDate today, DossierLook look) {
        String accent = look.accent() == null ? null : look.accent().substring(1);
        DocxDocument docx = new DocxDocument("Liberation Sans", (String) null);
        docx.paragraph(List.of(new DocxDocument.Run(dossier.name(), true, DocxDocument.TITLE, accent),
            new DocxDocument.Run("    " + DossierLabel.TITLE.in(language), false, SUBTITLE, GREY)), DocxDocument.Align.LEFT, 60);
        List<String> subtitle = new ArrayList<>();
        if (dossier.position() != null) {
            subtitle.add(dossier.position());
        }
        if (dossier.city() != null) {
            subtitle.add(dossier.city());
        }
        if (!subtitle.isEmpty()) {
            docx.paragraph(String.join(" · ", subtitle), false, SUBTITLE, DocxDocument.Align.LEFT);
        }
        if (!dossier.contact().isEmpty()) {
            docx.paragraph(List.of(new DocxDocument.Run(String.join(" · ", dossier.contact()), false, DocxDocument.SMALL, GREY)),
                DocxDocument.Align.LEFT, 60);
        }
        docx.rule(accent);
        section(docx, accent, DossierLabel.SUMMARY.in(language), dossier.summary());
        section(docx, accent, DossierLabel.SKILLS.in(language), String.join(" · ", dossier.skills().stream()
            .map(skill -> DossierPage.skill(skill, language)).toList()));
        section(docx, accent, DossierLabel.LANGUAGES.in(language), String.join(", ", dossier.languages()));
        section(docx, accent, DossierLabel.CERTIFICATIONS.in(language), String.join(" · ", dossier.certifications()));
        if (!dossier.missions().isEmpty() || dossier.positions().isEmpty()) {
            heading(docx, accent, DossierLabel.MISSIONS.in(language));
            if (dossier.missions().isEmpty()) {
                body(docx, DossierLabel.NO_MISSIONS.in(language));
            }
            entries(docx, look.compact() ? dossier.missions().stream().limit(DossierPage.COMPACT_MISSIONS).toList() : dossier.missions(), language,
                look);
        }
        if (!dossier.positions().isEmpty()) {
            heading(docx, accent, (dossier.missions().isEmpty() ? DossierLabel.MISSIONS : DossierLabel.POSITIONS).in(language));
            entries(docx, look.compact() ? dossier.positions().stream().limit(DossierPage.COMPACT_MISSIONS).toList() : dossier.positions(), language,
                look);
        }
        if (!dossier.education().isEmpty()) {
            heading(docx, accent, DossierLabel.EDUCATION.in(language));
            for (DossierDto.Education school : dossier.education()) {
                String years = school.startYear() != null && school.endYear() != null ? school.startYear() + " – " + school.endYear() :
                    school.endYear() != null ? String.valueOf(school.endYear()) : school.startYear() != null ? String.valueOf(school.startYear()) : null;
                String line = school.degree() != null ? school.degree() + " · " + school.school() : school.school();
                body(docx, years == null ? line : line + " (" + years + ")");
            }
        }
        docx.paragraph("", false, DocxDocument.BODY, DocxDocument.Align.LEFT);
        docx.paragraph(List.of(new DocxDocument.Run(DossierLabel.FOOTER.format(language,
            today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", language.locale()))), false, DocxDocument.SMALL, GREY)), DocxDocument.Align.LEFT, 0);
        return docx.save(DossierLabel.TITLE.in(language) + " – " + dossier.name(), dossier.name());
    }

    private static void entries(DocxDocument docx, List<DossierDto.Mission> entries, DocumentLanguage language, DossierLook look) {
        for (DossierDto.Mission mission : entries) {
            docx.paragraph(List.of(new DocxDocument.Run(mission.role() + " · " +
                (mission.client() != null ? mission.client() : DossierLabel.CLIENT_HIDDEN.in(language)), true, DocxDocument.BODY, null)),
                DocxDocument.Align.LEFT, 20);
            String period = DossierPage.period(mission, language);
            if (!period.isEmpty()) {
                docx.paragraph(List.of(new DocxDocument.Run(period, false, DocxDocument.SMALL, GREY)), DocxDocument.Align.LEFT, 40);
            }
            if (mission.description() != null) {
                for (String paragraph : (look.compact() ? DossierPage.cut(mission.description()) : mission.description()).split("\\R")) {
                    if (!paragraph.isBlank()) {
                        body(docx, paragraph.strip());
                    }
                }
            }
            if (mission.stack() != null) {
                docx.paragraph(List.of(new DocxDocument.Run(DossierLabel.STACK.format(language, mission.stack()), false, DocxDocument.SMALL, GREY)),
                    DocxDocument.Align.LEFT, 160);
            }
        }
    }

    /** A heading and its text, left out when there is nothing to say. */
    private static void section(DocxDocument docx, String accent, String heading, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        heading(docx, accent, heading);
        for (String paragraph : text.split("\\R")) {
            if (!paragraph.isBlank()) {
                body(docx, paragraph.strip());
            }
        }
    }

    private static void heading(DocxDocument docx, String accent, String heading) {
        docx.paragraph("", false, DocxDocument.SMALL, DocxDocument.Align.LEFT);
        docx.paragraph(List.of(new DocxDocument.Run(heading, true, HEADING, accent)), DocxDocument.Align.LEFT, 80);
    }

    private static void body(DocxDocument docx, String text) {
        docx.paragraph(List.of(new DocxDocument.Run(text, false, DocxDocument.BODY, null)), DocxDocument.Align.LEFT, 80);
    }
}
