package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.media.NetworkDocuments;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a CV, as a PDF or a Word or OpenDocument text, into the profile's fields (see docs/features.md#ai-features): the
 * name, phone, city, current position and company, presentation, links, languages, skills with their years and
 * certifications. Nothing is kept: the freelancer picks what to put in the profile.
 */
@Service
@RequiredArgsConstructor
public class ProfileImportAiService {

    static final int FILE_MAX_BYTES = 10 * 1024 * 1024;
    private static final int TEXT_MAX = 20_000;
    private static final int SKILLS_MAX = 50;
    private static final int YEARS_MAX = 50;
    private static final int CERTIFICATIONS_MAX = 30;
    private static final int NAME_MAX = 200;
    private static final Pattern DATE = Pattern.compile("(\\d{4})-(\\d{1,2})(?:-(\\d{1,2}))?");
    private static final Set<String> TEXT_FORMATS = Set.of("pdf", "docx", "odt");
    private static final long MAX_TOKENS = 5_000;
    private static final String SYSTEM = """
        You read a French freelance IT consultant's CV into their profile's fields. Fill only what the CV states; leave the
        rest empty or 0. The presentation is the CV's profile or summary paragraph, as written. Skills are technologies,
        languages, frameworks and methods, each with the years of experience the dated positions mentioning it suggest
        (0 when unclear). Spoken languages go in languages, not skills. Certifications are the ones the CV names, with
        their issuer and dates when given.""";

    private final AiService ai;

    /** A skill and its years. */
    public record Skill(@JsonPropertyDescription("The skill as usually written") String name,
                        @JsonPropertyDescription("Years of experience with it, or 0") int years) {
    }

    /** A certification and its dates, as {@code YYYY-MM-DD} or {@code YYYY-MM}. */
    public record Certification(@JsonPropertyDescription("The certification's name, e.g. AWS Certified Solutions Architect") String name,
                                @JsonPropertyDescription("Who issued it, e.g. Amazon Web Services") String authority,
                                @JsonPropertyDescription("When it was obtained, as YYYY-MM-DD or YYYY-MM, or empty") String obtainedOn,
                                @JsonPropertyDescription("When it expires, as YYYY-MM-DD or YYYY-MM, or empty") String expiresOn) {
    }

    /** What the CV says. */
    public record Reading(
        String firstName, String lastName, String phone, String city,
        @JsonPropertyDescription("The current or latest job title") String position,
        @JsonPropertyDescription("The current or latest company, or the freelancer's own") String company,
        @JsonPropertyDescription("The CV's profile or summary paragraph") String bio,
        String website, String linkedin, String github,
        @JsonPropertyDescription("Spoken languages, each named in French in one word: Français, Anglais, Espagnol, Allemand, Italien, Portugais, " +
            "Chinois, Japonais, Arabe, Russe, Néerlandais or another") List<String> languages,
        List<Skill> skills,
        @JsonPropertyDescription("Certifications the CV names, not trainings without one") List<Certification> certifications) {

        public Reading {
            languages = languages == null ? List.of() : List.copyOf(languages.stream().filter(Objects::nonNull).toList());
            skills = skills == null ? List.of() : List.copyOf(skills.stream().filter(Objects::nonNull).toList());
            certifications = certifications == null ? List.of() : List.copyOf(certifications.stream().filter(Objects::nonNull).toList());
        }
    }

    public Reading read(Long userId, String fileName, byte[] content) {
        if (content.length == 0 || content.length > FILE_MAX_BYTES) {
            throw new FieldRejectedException("file", "A CV of 10 MB at most");
        }
        NetworkDocuments.Type type;
        try {
            type = NetworkDocuments.detect(fileName, content);
        } catch (IllegalArgumentException e) {
            throw new FieldRejectedException("file", "Send the CV as a PDF or a Word document (docx)");
        }
        if (!TEXT_FORMATS.contains(type.extension())) {
            throw new FieldRejectedException("file", "Send the CV as a PDF or a Word document (docx)");
        }
        String text = NetworkDocuments.text(type, content);
        if (text.isBlank()) {
            throw new FieldRejectedException("file", "This CV has no text to read: it may be a scanned page");
        }
        Reading reading = ai.structured(userId, AiFeature.POSTING, SYSTEM, "<cv>\n" + text.substring(0, Math.min(text.length(), TEXT_MAX)) +
            "\n</cv>", Reading.class, MAX_TOKENS, OutputConfig.Effort.LOW);
        return clean(reading);
    }

    public static Reading clean(Reading reading) {
        return new Reading(blank(reading.firstName()), blank(reading.lastName()), blank(reading.phone()), blank(reading.city()),
            blank(reading.position()), blank(reading.company()), blank(reading.bio()), blank(reading.website()), blank(reading.linkedin()),
            blank(reading.github()),
            reading.languages().stream().map(String::strip).filter(language -> !language.isEmpty()).distinct().toList(),
            reading.skills().stream().filter(skill -> skill.name() != null && !skill.name().isBlank())
                .map(skill -> new Skill(skill.name().strip(), Math.clamp(skill.years(), 0, YEARS_MAX)))
                .filter(distinctByName()).limit(SKILLS_MAX).toList(),
            reading.certifications().stream().filter(certification -> certification.name() != null && !certification.name().isBlank())
                .map(certification -> new Certification(clip(certification.name()), clip(blank(certification.authority())),
                    date(certification.obtainedOn()), date(certification.expiresOn())))
                .filter(distinct(Certification::name)).limit(CERTIFICATIONS_MAX).toList());
    }

    /** A date as {@code YYYY-MM-DD}, a month taken as its first day; null when it cannot be read. */
    public static String date(String value) {
        if (value == null) {
            return null;
        }
        Matcher date = DATE.matcher(value.strip());
        if (!date.matches()) {
            return null;
        }
        try {
            return LocalDate.of(Integer.parseInt(date.group(1)), Integer.parseInt(date.group(2)),
                date.group(3) == null ? 1 : Integer.parseInt(date.group(3))).toString();
        } catch (DateTimeException e) {
            return null;
        }
    }

    /** At most the 200 characters a training's title and provider hold. */
    private static String clip(String value) {
        return value == null || value.length() <= NAME_MAX ? value : value.substring(0, NAME_MAX).strip();
    }

    private static Predicate<Skill> distinctByName() {
        return distinct(Skill::name);
    }

    private static <T> Predicate<T> distinct(Function<T, String> name) {
        Set<String> seen = new HashSet<>();
        return item -> seen.add(name.apply(item).toLowerCase(Locale.ROOT));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
