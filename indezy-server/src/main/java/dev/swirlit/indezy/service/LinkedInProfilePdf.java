package dev.swirlit.indezy.service;

import dev.swirlit.indezy.ai.ProfileImportAiService;
import dev.swirlit.indezy.exception.FieldRejectedException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the PDF of LinkedIn's **Save to PDF** (one's own profile → More → Save to PDF) into the profile's fields, without AI
 * (see docs/features.md#profile-import). Its layout is fixed: a left column with Contact, Top Skills, Languages and
 * Certifications, and a main column with the name (the largest text), the headline, the place, then Summary, Experience
 * and Education, headed in English or French. Lines are told apart by where they start on the page, not by their order.
 */
public final class LinkedInProfilePdf {

    private static final int MAX_PAGES = 20;
    /** The left column ends before this share of the page's width. */
    private static final float SIDEBAR = 0.33f;
    private static final Pattern PAGE = Pattern.compile("(?i)^page \\d+ (of|sur) \\d+$");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d[\\d .()-]{7,}\\d");
    private static final Pattern LINK = Pattern.compile("^(\\S+)\\s*\\((\\p{L}+)\\)$");
    private static final Pattern DATES = Pattern.compile("^(.+?)\\s+[-–]\\s+(.+?)(\\s*\\(.*\\))?$");
    private static final Pattern DURATION = Pattern.compile("(?i)^(\\d+\\s+(years?|ans?|months?|mois)\\s*)+$");
    private static final Pattern PRESENT = Pattern.compile("(?i)^(present|présent|aujourd['’]hui|now|actuel(lement)?)$");
    private static final Pattern PROFICIENCY = Pattern.compile("\\s*\\(.*\\)$");

    private enum Section { CONTACT, SKILLS, LANGUAGES, CERTIFICATIONS, OTHER, SUMMARY, EXPERIENCE, EDUCATION }

    private static final Map<String, Section> HEADINGS = Map.ofEntries(
        Map.entry("contact", Section.CONTACT), Map.entry("coordonnées", Section.CONTACT),
        Map.entry("top skills", Section.SKILLS), Map.entry("principales compétences", Section.SKILLS),
        Map.entry("compétences principales", Section.SKILLS), Map.entry("languages", Section.LANGUAGES),
        Map.entry("langues", Section.LANGUAGES), Map.entry("certifications", Section.CERTIFICATIONS),
        Map.entry("honors-awards", Section.OTHER), Map.entry("distinctions", Section.OTHER), Map.entry("publications", Section.OTHER),
        Map.entry("patents", Section.OTHER), Map.entry("brevets", Section.OTHER), Map.entry("summary", Section.SUMMARY),
        Map.entry("résumé", Section.SUMMARY), Map.entry("experience", Section.EXPERIENCE), Map.entry("expérience", Section.EXPERIENCE),
        Map.entry("education", Section.EDUCATION), Map.entry("formation", Section.EDUCATION));

    private LinkedInProfilePdf() {
    }

    /** A line of text, its largest letters, and whether it starts in the left column. */
    record Line(String text, float size, boolean side) {
    }

    public static ProfileImportAiService.Reading read(byte[] pdf, LocalDate today) {
        List<Line> lines = lines(pdf);
        List<Line> main = lines.stream().filter(line -> !line.side()).toList();
        Map<Section, List<String>> sections = new EnumMap<>(Section.class);
        List<Line> experience = new ArrayList<>();
        List<String> top = new ArrayList<>();
        Line name = null;
        Section current = null;
        for (Line line : lines.stream().filter(Line::side).toList()) {
            Section heading = HEADINGS.get(line.text().toLowerCase(Locale.ROOT));
            if (heading != null && heading.ordinal() <= Section.OTHER.ordinal()) {
                current = heading;
            } else if (current != null) {
                sections.computeIfAbsent(current, section -> new ArrayList<>()).add(line.text());
            }
        }
        current = null;
        for (Line line : main) {
            Section heading = HEADINGS.get(line.text().toLowerCase(Locale.ROOT));
            if (heading != null && heading.ordinal() > Section.OTHER.ordinal()) {
                current = heading;
                sections.computeIfAbsent(current, section -> new ArrayList<>());
            } else if (current != null) {
                sections.computeIfAbsent(current, section -> new ArrayList<>()).add(line.text());
                if (current == Section.EXPERIENCE) {
                    experience.add(line);
                }
            } else if (name == null || line.size() > name.size()) {
                name = line;
                top.clear();
            } else {
                top.add(line.text());
            }
        }
        if (name == null || sections.isEmpty()) {
            throw new FieldRejectedException("file", "This PDF is not a LinkedIn profile: use Save to PDF on your own profile");
        }
        String headline = top.size() > 1 ? String.join(" ", top.subList(0, top.size() - 1)) : top.stream().findFirst().orElse(null);
        String place = top.size() > 1 ? top.getLast() : null;
        List<LinkedInProfileImport.Position> positions = positions(experience);
        Optional<LinkedInProfileImport.Position> latest = positions.stream().findFirst();
        Contact contact = contact(sections.getOrDefault(Section.CONTACT, List.of()));
        String[] names = name.text().split("\\s+", 2);
        YearMonth now = YearMonth.from(today);
        List<ProfileImportAiService.Skill> skills = sections.getOrDefault(Section.SKILLS, List.of()).stream()
            .map(skill -> new ProfileImportAiService.Skill(skill, LinkedInProfileImport.years(skill, positions, now))).toList();
        List<String> languages = sections.getOrDefault(Section.LANGUAGES, List.of()).stream()
            .map(language -> PROFICIENCY.matcher(language).replaceAll("").strip()).filter(language -> !language.isEmpty())
            .map(LinkedInProfileImport::language).toList();
        List<ProfileImportAiService.Certification> certifications = sections.getOrDefault(Section.CERTIFICATIONS, List.of()).stream()
            .map(certification -> new ProfileImportAiService.Certification(certification, null, null, null)).toList();
        String summary = sections.containsKey(Section.SUMMARY) ? String.join(" ", sections.get(Section.SUMMARY)) : null;
        return ProfileImportAiService.clean(new ProfileImportAiService.Reading(names[0], names.length > 1 ? names[1] : null, contact.phone(),
            place == null ? null : place.split(",")[0], latest.map(LinkedInProfileImport.Position::title).orElse(headline),
            latest.map(LinkedInProfileImport.Position::company).orElse(null), summary != null ? summary : headline, contact.website(),
            contact.linkedin(), contact.github(), languages, skills, certifications));
    }

    /** The left column's contact details, a link's kind written after it ("www.linkedin.com/in/alice (LinkedIn)"). */
    record Contact(String phone, String website, String linkedin, String github) {
    }

    static Contact contact(List<String> lines) {
        String phone = null;
        String website = null;
        String linkedin = null;
        String github = null;
        List<String> joined = new ArrayList<>();
        for (String line : lines) {
            // A long LinkedIn address runs onto the next line.
            if (!joined.isEmpty() && joined.getLast().contains("linkedin.com/") && !joined.getLast().endsWith(")")) {
                joined.set(joined.size() - 1, joined.getLast() + line);
            } else {
                joined.add(line);
            }
        }
        for (String line : joined) {
            Matcher link = LINK.matcher(line);
            if (EMAIL.matcher(line).find()) {
                continue;
            }
            if (link.matches() && link.group(1).contains(".")) {
                String url = link.group(1).startsWith("http") ? link.group(1) : "https://" + link.group(1);
                if (url.contains("linkedin.com/")) {
                    linkedin = Objects.requireNonNullElse(linkedin, url);
                } else if (url.contains("github.com/")) {
                    github = Objects.requireNonNullElse(github, url);
                } else {
                    website = Objects.requireNonNullElse(website, url);
                }
            } else if (phone == null && PHONE.matcher(line).find()) {
                Matcher number = PHONE.matcher(line);
                phone = number.find() ? number.group().strip() : null;
            }
        }
        return new Contact(phone, website, linkedin, github);
    }

    /**
     * The Experience section's positions, latest first as LinkedIn lists them: each found by its dates line ("January 2020 -
     * Present (4 years 11 months)") with its title on the line above. Above the title, past a company's total duration, a
     * line in larger letters names a new company; otherwise the role is another one at the same company.
     */
    static List<LinkedInProfileImport.Position> positions(List<Line> lines) {
        List<Integer> dated = new ArrayList<>();
        List<Integer> headers = new ArrayList<>();
        List<String> companies = new ArrayList<>();
        String company = null;
        for (int index = 1; index < lines.size(); index++) {
            if (dates(lines.get(index).text()) == null) {
                continue;
            }
            int title = index - 1;
            int above = title - 1;
            while (above >= 0 && DURATION.matcher(lines.get(above).text()).matches()) {
                above--;
            }
            int previous = dated.isEmpty() ? -1 : dated.getLast();
            boolean named = above > previous && (dated.isEmpty() || lines.get(above).size() > lines.get(title).size());
            if (named) {
                company = lines.get(above).text();
            }
            dated.add(index);
            headers.add(named ? above : title);
            companies.add(company);
        }
        List<LinkedInProfileImport.Position> positions = new ArrayList<>();
        for (int at = 0; at < dated.size(); at++) {
            int index = dated.get(at);
            int end = at + 1 < dated.size() ? headers.get(at + 1) : lines.size();
            String description = String.join(" ", lines.subList(index + 1, Math.max(index + 1, end)).stream().map(Line::text).toList());
            Period period = dates(lines.get(index).text());
            positions.add(new LinkedInProfileImport.Position(companies.get(at), lines.get(index - 1).text(), description, period.start(),
                period.end()));
        }
        return positions;
    }

    /** A position's start and end, the end null while it runs. */
    record Period(YearMonth start, YearMonth end) {
    }

    /** A dates line's period; null when the line is not one. */
    static Period dates(String line) {
        Matcher dates = DATES.matcher(line.strip());
        if (!dates.matches()) {
            return null;
        }
        YearMonth start = LinkedInProfileImport.month(dates.group(1));
        String to = dates.group(2).strip();
        boolean running = PRESENT.matcher(to).matches();
        YearMonth end = running ? null : LinkedInProfileImport.month(to);
        return start == null || !running && end == null ? null : new Period(start, end);
    }

    private static List<Line> lines(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.isEncrypted()) {
                throw new FieldRejectedException("file", "This PDF is protected: save it without a password");
            }
            LineStripper stripper = new LineStripper();
            stripper.setEndPage(MAX_PAGES);
            stripper.writeText(document, new StringWriter());
            stripper.flush();
            return stripper.lines.stream().filter(line -> !PAGE.matcher(line.text()).matches()).toList();
        } catch (InvalidPasswordException e) {
            throw new FieldRejectedException("file", "This PDF is protected: save it without a password");
        } catch (IOException e) {
            throw new FieldRejectedException("file", "This file is not a PDF that can be read");
        }
    }

    /** Collects the text line by line, in the order it is drawn, with where each line starts and its largest letters. */
    private static final class LineStripper extends PDFTextStripper {
        private final List<Line> lines = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private float size;
        private float x = -1;
        private float width = 612;

        LineStripper() throws IOException {
            setSortByPosition(false);
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            flush();
            width = page.getMediaBox().getWidth();
            super.startPage(page);
        }

        @Override
        protected void writeString(String string, List<TextPosition> positions) {
            for (TextPosition position : positions) {
                if (x < 0) {
                    x = position.getXDirAdj();
                }
                size = Math.max(size, position.getFontSizeInPt());
            }
            text.append(string);
        }

        @Override
        protected void writeWordSeparator() {
            text.append(' ');
        }

        @Override
        protected void writeLineSeparator() {
            flush();
        }

        @Override
        protected void writeParagraphStart() {
            flush();
        }

        @Override
        protected void writeParagraphEnd() {
            flush();
        }

        void flush() {
            String line = text.toString().replace(' ', ' ').strip().replaceAll("\\s+", " ");
            if (!line.isEmpty()) {
                lines.add(new Line(line, size, x < width * SIDEBAR));
            }
            text.setLength(0);
            size = 0;
            x = -1;
        }
    }
}
