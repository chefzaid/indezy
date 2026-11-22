package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.media.NetworkDocuments;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reads a CV, as a PDF or a Word or OpenDocument text, into the profile's fields (see docs/features.md#ai-features): the
 * name, phone, city, current position and company, presentation, links, languages and skills with their years. Nothing is
 * kept: the freelancer picks what to put in the profile.
 */
@Service
@RequiredArgsConstructor
public class ProfileImportAiService {

    static final int FILE_MAX_BYTES = 10 * 1024 * 1024;
    private static final int TEXT_MAX = 20_000;
    private static final int SKILLS_MAX = 50;
    private static final int YEARS_MAX = 50;
    private static final Set<String> TEXT_FORMATS = Set.of("pdf", "docx", "odt");
    private static final long MAX_TOKENS = 4_000;
    private static final String SYSTEM = """
        You read a French freelance IT consultant's CV into their profile's fields. Fill only what the CV states; leave the
        rest empty or 0. The presentation is the CV's profile or summary paragraph, as written. Skills are technologies,
        languages, frameworks and methods, each with the years of experience the dated positions mentioning it suggest
        (0 when unclear). Spoken languages go in languages, not skills.""";

    private final AiService ai;

    /** A skill and its years. */
    public record Skill(@JsonPropertyDescription("The skill as usually written") String name,
                        @JsonPropertyDescription("Years of experience with it, or 0") int years) {
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
        List<Skill> skills) {

        public Reading {
            languages = languages == null ? List.of() : List.copyOf(languages.stream().filter(Objects::nonNull).toList());
            skills = skills == null ? List.of() : List.copyOf(skills.stream().filter(Objects::nonNull).toList());
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
                .filter(distinctByName()).limit(SKILLS_MAX).toList());
    }

    private static Predicate<Skill> distinctByName() {
        Set<String> seen = new HashSet<>();
        return skill -> seen.add(skill.name().toLowerCase(Locale.ROOT));
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
