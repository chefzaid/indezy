package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lays out a dossier de compétences: who, a summary, skills, languages, certifications, then the missions. */
final class DossierPage {

    private static final float TITLE = 20;
    private static final float HEADING = 12.5f;
    private static final float BODY = 9.5f;
    private static final float SMALL = 8;
    private static final float GAP = 14;

    private final PdfPageWriter writer;
    private final DossierDto dossier;
    private final DocumentLanguage language;
    private final DateTimeFormatter monthYear;

    DossierPage(PdfPageWriter writer, DossierDto dossier, DocumentLanguage language) {
        this.writer = writer;
        this.dossier = dossier;
        this.language = language;
        this.monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", language.locale());
    }

    String title() {
        return DossierLabel.TITLE.in(language) + " – " + dossier.name();
    }

    void write(LocalDate today) throws IOException {
        header();
        section(DossierLabel.SUMMARY, dossier.summary());
        section(DossierLabel.SKILLS, String.join(" · ", dossier.skills().stream().map(this::skill).toList()));
        section(DossierLabel.LANGUAGES, String.join(", ", dossier.languages()));
        section(DossierLabel.CERTIFICATIONS, String.join(" · ", dossier.certifications()));
        missions();
        writer.ensureSpace(GAP * 2);
        writer.moveDown(GAP * 2);
        writer.text(writer.left(), writer.cursor(), DossierLabel.FOOTER.format(language,
            today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", language.locale()))), false, SMALL);
    }

    private void header() throws IOException {
        writer.moveDown(TITLE);
        writer.text(writer.left(), writer.cursor(), dossier.name(), true, TITLE);
        writer.textRight(writer.right(), writer.cursor(), DossierLabel.TITLE.in(language), false, 11);
        List<String> subtitle = new ArrayList<>();
        if (dossier.position() != null) {
            subtitle.add(dossier.position());
        }
        if (dossier.city() != null) {
            subtitle.add(dossier.city());
        }
        if (!subtitle.isEmpty()) {
            writer.paragraph(String.join(" · ", subtitle), false, 11, writer.width());
        }
        if (!dossier.contact().isEmpty()) {
            writer.paragraph(String.join(" · ", dossier.contact()), false, SMALL, writer.width());
        }
        writer.moveDown(8);
        writer.line(writer.left(), writer.cursor(), writer.right(), writer.cursor());
    }

    /** A heading and its text, left out when there is nothing to say. */
    private void section(DossierLabel heading, String text) throws IOException {
        if (text == null || text.isBlank()) {
            return;
        }
        heading(heading);
        writer.paragraph(text, false, BODY, writer.width());
    }

    private void heading(DossierLabel heading) throws IOException {
        writer.ensureSpace(GAP + HEADING * 3);
        writer.moveDown(GAP + HEADING);
        writer.text(writer.left(), writer.cursor(), heading.in(language), true, HEADING);
        writer.moveDown(2);
    }

    private void missions() throws IOException {
        heading(DossierLabel.MISSIONS);
        if (dossier.missions().isEmpty()) {
            writer.paragraph(DossierLabel.NO_MISSIONS.in(language), false, BODY, writer.width());
            return;
        }
        for (DossierDto.Mission mission : dossier.missions()) {
            writer.ensureSpace(BODY * 6);
            writer.moveDown(6);
            writer.paragraph(mission.role() + " · " + (mission.client() != null ? mission.client() : DossierLabel.CLIENT_HIDDEN.in(language)),
                true, BODY, writer.width());
            String period = period(mission);
            if (!period.isEmpty()) {
                writer.paragraph(period, false, SMALL, writer.width());
            }
            if (mission.description() != null) {
                writer.paragraph(mission.description(), false, BODY, writer.width());
            }
            if (mission.stack() != null) {
                writer.paragraph(DossierLabel.STACK.format(language, mission.stack()), false, SMALL, writer.width());
            }
        }
    }

    /** "mars 2025 – février 2026", or "depuis mars 2025" while it runs open-ended; empty without a start. */
    private String period(DossierDto.Mission mission) {
        if (mission.startsOn() == null) {
            return "";
        }
        String start = capitalized(mission.startsOn().format(monthYear));
        return mission.endsOn() == null ? DossierLabel.SINCE.format(language, start) : start + " – " + mission.endsOn().format(monthYear);
    }

    private String skill(DossierDto.Skill skill) {
        List<String> details = new ArrayList<>();
        if (skill.level() != null) {
            details.add(DossierLabel.level(skill.level(), language));
        }
        if (skill.years() != null) {
            details.add(DossierLabel.YEARS.format(language, skill.years()));
        }
        return details.isEmpty() ? skill.name() : skill.name() + " (" + String.join(", ", details) + ")";
    }

    private String capitalized(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(language.locale()) + text.substring(1).toLowerCase(Locale.ROOT);
    }
}
