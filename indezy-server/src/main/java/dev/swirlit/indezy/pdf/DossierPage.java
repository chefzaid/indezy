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

    /** A compact dossier shows at most this many missions, and of each this many characters. */
    static final int COMPACT_MISSIONS = 8;
    static final int COMPACT_DESCRIPTION = 300;

    private final float title;
    private final float headingSize;
    private final float body;
    private final float small;
    private final float gap;
    private final PdfPageWriter writer;
    private final DossierDto dossier;
    private final DocumentLanguage language;
    private final DossierLook look;

    DossierPage(PdfPageWriter writer, DossierDto dossier, DocumentLanguage language) {
        this(writer, dossier, language, DossierLook.CLASSIC);
    }

    DossierPage(PdfPageWriter writer, DossierDto dossier, DocumentLanguage language, DossierLook look) {
        this.writer = writer;
        this.dossier = dossier;
        this.language = language;
        this.look = look == null ? DossierLook.CLASSIC : look;
        boolean compact = this.look.compact();
        this.title = compact ? 16 : 20;
        this.headingSize = compact ? 11 : 12.5f;
        this.body = compact ? 8.5f : 9.5f;
        this.small = compact ? 7.5f : 8;
        this.gap = compact ? 9 : 14;
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
        positions();
        education();
        writer.ensureSpace(gap * 2);
        writer.moveDown(gap * 2);
        writer.text(writer.left(), writer.cursor(), DossierLabel.FOOTER.format(language,
            today.format(DateTimeFormatter.ofPattern("d MMMM yyyy", language.locale()))), false, small);
    }

    private void header() throws IOException {
        writer.moveDown(title);
        writer.color(look.accent());
        writer.text(writer.left(), writer.cursor(), dossier.name(), true, title);
        writer.color(null);
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
            writer.paragraph(String.join(" · ", dossier.contact()), false, small, writer.width());
        }
        writer.moveDown(8);
        writer.color(look.accent());
        writer.line(writer.left(), writer.cursor(), writer.right(), writer.cursor());
        writer.color(null);
    }

    /** A heading and its text, left out when there is nothing to say. */
    private void section(DossierLabel heading, String text) throws IOException {
        if (text == null || text.isBlank()) {
            return;
        }
        heading(heading);
        writer.paragraph(text, false, body, writer.width());
    }

    private void heading(DossierLabel heading) throws IOException {
        writer.ensureSpace(gap + headingSize * 3);
        writer.moveDown(gap + headingSize);
        writer.color(look.accent());
        writer.text(writer.left(), writer.cursor(), heading.in(language), true, headingSize);
        writer.color(null);
        writer.moveDown(2);
    }

    private void missions() throws IOException {
        if (dossier.missions().isEmpty() && !dossier.positions().isEmpty()) {
            return;
        }
        heading(DossierLabel.MISSIONS);
        if (dossier.missions().isEmpty()) {
            writer.paragraph(DossierLabel.NO_MISSIONS.in(language), false, body, writer.width());
            return;
        }
        entries(dossier.missions());
    }

    /** The positions held before or outside Indezy, after the won missions. */
    private void positions() throws IOException {
        if (dossier.positions().isEmpty()) {
            return;
        }
        heading(dossier.missions().isEmpty() ? DossierLabel.MISSIONS : DossierLabel.POSITIONS);
        entries(dossier.positions());
    }

    private void education() throws IOException {
        if (dossier.education().isEmpty()) {
            return;
        }
        heading(DossierLabel.EDUCATION);
        for (DossierDto.Education school : dossier.education()) {
            writer.ensureSpace(body * 3);
            String years = school.startYear() != null && school.endYear() != null ? school.startYear() + " – " + school.endYear() :
                school.endYear() != null ? String.valueOf(school.endYear()) : school.startYear() != null ? String.valueOf(school.startYear()) : null;
            String line = school.degree() != null ? school.degree() + " · " + school.school() : school.school();
            writer.paragraph(years == null ? line : line + " (" + years + ")", false, body, writer.width());
        }
    }

    private void entries(List<DossierDto.Mission> entries) throws IOException {
        for (DossierDto.Mission mission : look.compact() ? entries.stream().limit(COMPACT_MISSIONS).toList() : entries) {
            writer.ensureSpace(body * 6);
            writer.moveDown(look.compact() ? 3 : 6);
            writer.paragraph(mission.role() + " · " + (mission.client() != null ? mission.client() : DossierLabel.CLIENT_HIDDEN.in(language)),
                true, body, writer.width());
            String period = period(mission);
            if (!period.isEmpty()) {
                writer.paragraph(period, false, small, writer.width());
            }
            if (mission.description() != null) {
                writer.paragraph(look.compact() ? cut(mission.description()) : mission.description(), false, body, writer.width());
            }
            if (mission.stack() != null) {
                writer.paragraph(DossierLabel.STACK.format(language, mission.stack()), false, small, writer.width());
            }
        }
    }

    /** A description cut at a word past {@value #COMPACT_DESCRIPTION} characters, with an ellipsis. */
    static String cut(String description) {
        if (description.length() <= COMPACT_DESCRIPTION) {
            return description;
        }
        int at = description.lastIndexOf(' ', COMPACT_DESCRIPTION);
        return description.substring(0, at > COMPACT_DESCRIPTION / 2 ? at : COMPACT_DESCRIPTION).strip() + "…";
    }

    private String period(DossierDto.Mission mission) {
        return period(mission, language);
    }

    /** "mars 2025 – février 2026", or "depuis mars 2025" while it runs open-ended; empty without a start. */
    static String period(DossierDto.Mission mission, DocumentLanguage language) {
        if (mission.startsOn() == null) {
            return "";
        }
        DateTimeFormatter monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", language.locale());
        String start = capitalized(mission.startsOn().format(monthYear), language);
        return mission.endsOn() == null ? DossierLabel.SINCE.format(language, start) : start + " – " + mission.endsOn().format(monthYear);
    }

    private String skill(DossierDto.Skill skill) {
        return skill(skill, language);
    }

    /** "Java (Expert, 8 ans)": the skill with its level and years. */
    static String skill(DossierDto.Skill skill, DocumentLanguage language) {
        List<String> details = new ArrayList<>();
        if (skill.level() != null) {
            details.add(DossierLabel.level(skill.level(), language));
        }
        if (skill.years() != null) {
            details.add(DossierLabel.YEARS.format(language, skill.years()));
        }
        return details.isEmpty() ? skill.name() : skill.name() + " (" + String.join(", ", details) + ")";
    }

    private static String capitalized(String text, DocumentLanguage language) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(language.locale()) + text.substring(1).toLowerCase(Locale.ROOT);
    }
}
