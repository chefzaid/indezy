package dev.swirlit.indezy.service;

import dev.swirlit.indezy.ai.ProfileImportAiService;
import dev.swirlit.indezy.util.SkillNames;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a CV's text into the profile's fields without AI (see docs/features.md#profile-import): contact details by their
 * patterns, the sections by their usual headings in French and English ("Expérience professionnelle", "Compétences",
 * "Formation", "Langues", "Certifications", "Profil"), positions by their date ranges ("mars 2022 – aujourd'hui",
 * "2019 - 2021"), and skills among the known skill names wherever the CV names them. It reads what a plain CV says; one
 * in columns or tables reads better with AI.
 */
public final class CvTextReader {

    private static final int TOP_LINES = 8;
    private static final int NAME_WORDS = 4;
    private static final int LINE_MAX = 120;
    private static final Pattern PHONE = Pattern.compile("(?<![\\d+])(?:\\+33\\s?|0)[1-9](?:[\\s.-]?\\d{2}){4}(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern URL = Pattern.compile("(?:https?://)?(?:www\\.)?[\\w-]+(?:\\.[\\w-]+)+(?:/[\\w./%-]*)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern POSTAL_CITY = Pattern.compile("\\b\\d{5}\\s+(\\p{Lu}[\\p{L}' -]{1,40})");
    private static final Pattern CITY_COUNTRY = Pattern.compile("^(\\p{Lu}[\\p{L}' -]{1,40}),\\s*France\\b");
    private static final String MONTH = "(?:janv(?:ier)?|f[ée]vr?(?:ier)?|mars|avr(?:il)?|mai|juin|juil(?:let)?|ao[uû]t|sept?(?:embre)?|oct(?:obre)?" +
        "|nov(?:embre)?|d[ée]c(?:embre)?|jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:tember)?" +
        "|october|november|dec(?:ember)?)\\.?";
    private static final String WHEN = "(?:(?:" + MONTH + "\\s+)?(?:19|20)\\d{2}|(?:0?[1-9]|1[0-2])/(?:19|20)\\d{2})";
    private static final Pattern RANGE = Pattern.compile("(?iu)(?:de\\s+|from\\s+|depuis\\s+)?(" + WHEN + ")\\s*(?:[-–—]|à|a|to|au)\\s*(" + WHEN +
        "|aujourd['’]hui|pr[ée]sent|present|now|ce jour|en cours|actuel(?:lement)?)");
    private static final Pattern SINCE = Pattern.compile("(?iu)(?:depuis|since)\\s+(" + WHEN + ")");
    private static final Pattern YEARS = Pattern.compile("\\b((?:19|20)\\d{2})\\b");
    private static final Pattern SEPARATORS = Pattern.compile("\\s+(?:[-–—|@·•]|chez|at)\\s+|,\\s+");
    private static final Pattern BULLET = Pattern.compile("^[•▪●◦*·>-]\\s*");
    private static final Pattern LIST = Pattern.compile("\\s*[,;•|/]\\s*|\\s+-\\s+");

    private enum Section { SUMMARY, SKILLS, EXPERIENCE, EDUCATION, LANGUAGES, CERTIFICATIONS, OTHER }

    private static final Map<String, Section> HEADINGS = Map.ofEntries(
        Map.entry("profil", Section.SUMMARY), Map.entry("profile", Section.SUMMARY), Map.entry("résumé", Section.SUMMARY),
        Map.entry("summary", Section.SUMMARY), Map.entry("à propos", Section.SUMMARY), Map.entry("a propos", Section.SUMMARY),
        Map.entry("about", Section.SUMMARY), Map.entry("about me", Section.SUMMARY), Map.entry("présentation", Section.SUMMARY),
        Map.entry("compétences", Section.SKILLS), Map.entry("compétences techniques", Section.SKILLS), Map.entry("skills", Section.SKILLS),
        Map.entry("technical skills", Section.SKILLS), Map.entry("compétences clés", Section.SKILLS), Map.entry("savoir-faire", Section.SKILLS),
        Map.entry("environnement technique", Section.SKILLS), Map.entry("expérience", Section.EXPERIENCE), Map.entry("expériences", Section.EXPERIENCE),
        Map.entry("expérience professionnelle", Section.EXPERIENCE), Map.entry("expériences professionnelles", Section.EXPERIENCE),
        Map.entry("parcours professionnel", Section.EXPERIENCE), Map.entry("experience", Section.EXPERIENCE),
        Map.entry("work experience", Section.EXPERIENCE), Map.entry("professional experience", Section.EXPERIENCE),
        Map.entry("formation", Section.EDUCATION), Map.entry("formations", Section.EDUCATION), Map.entry("diplômes", Section.EDUCATION),
        Map.entry("études", Section.EDUCATION), Map.entry("education", Section.EDUCATION), Map.entry("langues", Section.LANGUAGES),
        Map.entry("languages", Section.LANGUAGES), Map.entry("certifications", Section.CERTIFICATIONS),
        Map.entry("certificats", Section.CERTIFICATIONS), Map.entry("loisirs", Section.OTHER), Map.entry("centres d'intérêt", Section.OTHER),
        Map.entry("intérêts", Section.OTHER), Map.entry("hobbies", Section.OTHER), Map.entry("interests", Section.OTHER),
        Map.entry("références", Section.OTHER), Map.entry("projets", Section.OTHER), Map.entry("projects", Section.OTHER));

    private CvTextReader() {
    }

    public static ProfileImportAiService.Reading read(String text, LocalDate today) {
        List<String> lines = text.replace('\u00a0', ' ').lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        Map<Section, List<String>> sections = new EnumMap<>(Section.class);
        List<String> top = new ArrayList<>();
        Section current = null;
        for (String line : lines) {
            Section heading = heading(line);
            if (heading != null) {
                current = heading;
                sections.computeIfAbsent(current, section -> new ArrayList<>());
            } else if (current == null) {
                top.add(line);
            } else {
                sections.get(current).add(line);
            }
        }
        String phone = first(PHONE, text).map(number -> number.replaceAll("[.-]", " ").replaceAll("\\s+", " ").strip()).orElse(null);
        List<String> links = links(text);
        String linkedin = links.stream().filter(link -> link.contains("linkedin.com/")).findFirst().orElse(null);
        String github = links.stream().filter(link -> link.contains("github.com/")).findFirst().orElse(null);
        String website = links.stream().filter(link -> !link.contains("linkedin.com") && !link.contains("github.com")).findFirst().orElse(null);
        List<String> header = top.stream().limit(TOP_LINES).filter(line -> !EMAIL.matcher(line).find() && !PHONE.matcher(line).find() &&
            !line.toLowerCase(Locale.ROOT).contains("http") && !line.toLowerCase(Locale.ROOT).contains("linkedin")).toList();
        Optional<String> name = header.stream().filter(CvTextReader::looksLikeName).findFirst();
        String[] names = name.map(found -> found.split("\\s+", 2)).orElse(new String[0]);
        String title = name.flatMap(found -> header.stream().dropWhile(line -> !line.equals(found)).skip(1)
            .filter(line -> city(line) == null && line.length() <= LINE_MAX).findFirst()).orElse(null);
        String city = top.stream().map(CvTextReader::city).filter(found -> found != null).findFirst().orElse(null);
        List<LinkedInProfileImport.Position> positions = positions(sections.getOrDefault(Section.EXPERIENCE, List.of()));
        YearMonth now = YearMonth.from(today);
        List<ProfileImportAiService.Skill> skills = skills(sections.getOrDefault(Section.SKILLS, List.of()), text).stream()
            .map(skill -> new ProfileImportAiService.Skill(skill, LinkedInProfileImport.years(skill, positions, now))).toList();
        List<String> languages = sections.getOrDefault(Section.LANGUAGES, List.of()).stream()
            .flatMap(line -> LIST.splitAsStream(BULLET.matcher(line).replaceFirst("")))
            .map(entry -> entry.replaceAll("\\s*[:(–-].*$", "").strip()).filter(entry -> entry.matches("\\p{L}{3,15}"))
            .map(LinkedInProfileImport::language).distinct().toList();
        List<ProfileImportAiService.Certification> certifications = sections.getOrDefault(Section.CERTIFICATIONS, List.of()).stream()
            .map(line -> BULLET.matcher(line).replaceFirst("").strip()).filter(line -> !line.isEmpty() && line.length() <= LINE_MAX)
            .map(line -> {
                Matcher year = YEARS.matcher(line);
                String obtained = year.find() ? year.group(1) + "-01" : null;
                String certification = YEARS.matcher(line).replaceAll("").replaceAll("[\\s(),–-]+$", "").replaceAll("^[\\s(),–-]+", "").strip();
                return new ProfileImportAiService.Certification(certification, null, obtained, null);
            }).filter(certification -> !certification.name().isEmpty()).toList();
        Optional<LinkedInProfileImport.Position> latest = positions.stream().findFirst();
        String summary = sections.containsKey(Section.SUMMARY) ? String.join(" ", sections.get(Section.SUMMARY)) : null;
        return ProfileImportAiService.clean(new ProfileImportAiService.Reading(names.length > 0 ? names[0] : null,
            names.length > 1 ? names[1] : null, phone, city, latest.map(LinkedInProfileImport.Position::title).orElse(title),
            latest.map(LinkedInProfileImport.Position::company).orElse(null), summary, website, linkedin, github, languages, skills,
            certifications, LinkedInProfileImport.pastPositions(positions), education(sections.getOrDefault(Section.EDUCATION, List.of()))));
    }

    /** The section a line heads, when it is one of the usual headings alone on its line. */
    static Section heading(String line) {
        String text = line.replaceAll("[:\\s]+$", "").strip().toLowerCase(Locale.ROOT);
        return text.length() > 40 ? null : HEADINGS.get(text);
    }

    /** A first and last name: two to four capitalised words, no digits. */
    static boolean looksLikeName(String line) {
        String[] words = line.split("\\s+");
        return words.length >= 2 && words.length <= NAME_WORDS && line.matches("[\\p{L}' -]+") &&
            java.util.Arrays.stream(words).allMatch(word -> Character.isUpperCase(word.codePointAt(0))) && heading(line) == null;
    }

    /** The town a line gives: after a postal code ("69002 Lyon") or before ", France". */
    static String city(String line) {
        Matcher postal = POSTAL_CITY.matcher(line);
        if (postal.find()) {
            return postal.group(1).replaceAll("[\\s-]+(Cedex|CEDEX).*$", "").strip();
        }
        Matcher country = CITY_COUNTRY.matcher(line);
        return country.find() ? country.group(1).strip() : null;
    }

    /** The web addresses the CV gives, e-mail domains apart, as links. */
    private static List<String> links(String text) {
        String withoutEmails = EMAIL.matcher(text).replaceAll(" ");
        Set<String> links = new LinkedHashSet<>();
        Matcher url = URL.matcher(withoutEmails);
        while (url.find()) {
            String found = url.group().replaceAll("[.,;)]+$", "");
            boolean webLike = found.startsWith("http") || found.startsWith("www.") || found.contains("linkedin.com/") || found.contains("github.com/");
            if (webLike) {
                links.add(found.startsWith("http") ? found : "https://" + found);
            }
        }
        return List.copyOf(links);
    }

    /** The skills section's entries, then the known skills the rest of the CV names, each once. */
    static List<String> skills(List<String> section, String text) {
        Set<String> skills = new LinkedHashSet<>();
        Set<String> keys = new java.util.HashSet<>();
        for (String line : section) {
            String entries = line.contains(":") ? line.substring(line.indexOf(':') + 1) : line;
            for (String entry : LIST.split(BULLET.matcher(entries).replaceFirst(""))) {
                String skill = entry.replaceAll("\\s*\\(.*\\)$", "").strip();
                if (skill.length() >= 1 && skill.length() <= 40 && skill.split("\\s+").length <= 4 && keys.add(SkillNames.key(SkillNames.canonical(skill)))) {
                    skills.add(SkillNames.canonical(skill));
                }
            }
        }
        for (String known : SkillNames.aliases().keySet()) {
            for (String variant : SkillNames.variants(known)) {
                Pattern named = Pattern.compile("(?<![\\p{L}\\p{N}+#.])" + Pattern.quote(variant) + "(?![\\p{L}\\p{N}+#])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                if (variant.length() > 1 && named.matcher(text).find() && keys.add(SkillNames.key(known))) {
                    skills.add(known);
                    break;
                }
            }
        }
        return List.copyOf(skills);
    }

    /**
     * The Experience section's positions, each found by its date range: the rest of that line, else the line above, names
     * the title and the employer, split at a dash, a comma, "chez" or "@"; the lines below up to the next one describe it.
     */
    static List<LinkedInProfileImport.Position> positions(List<String> lines) {
        List<Integer> dated = new ArrayList<>();
        List<LinkedInProfilePdf.Period> periods = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            LinkedInProfilePdf.Period period = period(lines.get(index));
            if (period != null) {
                dated.add(index);
                periods.add(period);
            }
        }
        List<LinkedInProfileImport.Position> positions = new ArrayList<>();
        for (int at = 0; at < dated.size(); at++) {
            int index = dated.get(at);
            String rest = BULLET.matcher(RANGE.matcher(SINCE.matcher(lines.get(index)).replaceAll("")).replaceAll("")).replaceFirst("")
                .replaceAll("^[\\s|:,()–-]+|[\\s|:,()–-]+$", "").strip();
            int header = index;
            if (rest.length() < 2 && index > 0 && (at == 0 || dated.get(at - 1) < index - 1)) {
                rest = lines.get(index - 1);
                header = index - 1;
            }
            String[] parts = SEPARATORS.split(rest, 2);
            String title = parts[0].strip();
            String employer = parts.length > 1 ? parts[1].strip() : null;
            if (employer == null && header > 0 && (at == 0 || dated.get(at - 1) < header - 1)) {
                employer = lines.get(header - 1);
            }
            int end = at + 1 < dated.size() ? dated.get(at + 1) - 1 : lines.size();
            String description = String.join(" ", lines.subList(Math.min(index + 1, lines.size()), Math.max(index + 1, end)));
            positions.add(new LinkedInProfileImport.Position(employer, title.isEmpty() ? null : title, description.isEmpty() ? null : description,
                periods.get(at).start(), periods.get(at).end()));
        }
        return positions.stream().filter(position -> position.title() != null).toList();
    }

    /** A line's date range, its end null while it runs; null when it holds none. */
    static LinkedInProfilePdf.Period period(String line) {
        Matcher range = RANGE.matcher(line);
        if (range.find()) {
            YearMonth start = when(range.group(1));
            return start == null ? null : new LinkedInProfilePdf.Period(start, when(range.group(2)));
        }
        Matcher since = SINCE.matcher(line);
        if (since.find() && when(since.group(1)) != null) {
            return new LinkedInProfilePdf.Period(when(since.group(1)), null);
        }
        return null;
    }

    private static YearMonth when(String text) {
        Matcher slash = Pattern.compile("^(\\d{1,2})/(\\d{4})$").matcher(text.strip());
        if (slash.matches()) {
            return YearMonth.of(Integer.parseInt(slash.group(2)), Integer.parseInt(slash.group(1)));
        }
        return LinkedInProfileImport.month(text.replace(".", ""));
    }

    /** The Education section's schools: a line with its years, the school and degree split at a comma or a dash. */
    static List<ProfileImportAiService.School> education(List<String> lines) {
        List<ProfileImportAiService.School> schools = new ArrayList<>();
        for (String line : lines) {
            Matcher year = YEARS.matcher(line);
            List<Integer> years = new ArrayList<>();
            while (year.find()) {
                years.add(Integer.parseInt(year.group(1)));
            }
            if (years.isEmpty()) {
                continue;
            }
            String rest = BULLET.matcher(YEARS.matcher(line).replaceAll("")).replaceFirst("").replaceAll("^[\\s|:,()–-]+|[\\s|:,()–-]+$", "")
                .replaceAll("\\s*[–-]\\s*[–-]\\s*", " - ").strip();
            if (rest.isEmpty()) {
                continue;
            }
            String[] parts = SEPARATORS.split(rest, 2);
            boolean degreeFirst = parts.length > 1 && parts[0].matches("(?iu).*(master|licence|bachelor|dipl[oô]me|bts|dut|mba|ing[ée]nieur|doctorat|phd|bac).*");
            String school = parts.length > 1 && degreeFirst ? parts[1] : parts[0];
            String degree = parts.length > 1 ? (degreeFirst ? parts[0] : parts[1]) : null;
            schools.add(new ProfileImportAiService.School(school.strip(), degree == null ? null : degree.strip(),
                years.size() > 1 ? years.getFirst() : 0, years.getLast()));
        }
        return schools;
    }

    private static Optional<String> first(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }
}
