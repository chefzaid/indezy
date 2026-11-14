package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DossierDto;
import dev.swirlit.indezy.dto.SkillDetailDto;
import dev.swirlit.indezy.dto.UserDto;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierLayout;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.pdf.DossierPdfRenderer;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The dossier de compétences (see docs/features.md#dossier-de-competences): the profile, the valid certifications and
 * the won missions as a PDF, named or anonymized, in French or English. Nothing is written by AI.
 */
@Service
@RequiredArgsConstructor
public class DossierService {

    /** A mission's description is cut to this many characters, at a word. */
    static final int DESCRIPTION_LENGTH = 600;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final UserService users;
    private final ProjectRepository projects;
    private final TrainingService trainings;
    private final DossierPdfRenderer renderer;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DossierDto dossier(Long userId, Long freelanceId, DossierLayout layout) {
        UserDto profile = users.getUserProfile(userId);
        boolean named = layout == DossierLayout.NAMED;
        Map<String, SkillDetailDto> details = profile.getSkillDetails() == null ? Map.of() : profile.getSkillDetails();
        List<DossierDto.Skill> skills = (profile.getSkills() == null ? List.<String>of() : profile.getSkills()).stream()
            .map(skill -> {
                SkillDetailDto detail = details.get(skill);
                return new DossierDto.Skill(skill, detail == null ? null : detail.level(), detail == null ? null : detail.years());
            })
            .toList();
        List<DossierDto.Mission> missions = projects.findByFreelanceId(freelanceId).stream()
            .filter(project -> project.getStatus() == ProjectStatus.WON)
            .sorted(Comparator.comparing(Project::getStartDate, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(project -> new DossierDto.Mission(project.getRole(),
                named && project.getClient() != null ? project.getClient().getCompanyName() : null,
                project.getStartDate(), project.lastDay(), blankToNull(project.getTechStack()), excerpt(project.getDescription())))
            .toList();
        return new DossierDto(named ? fullName(profile) : initials(profile), blankToNull(profile.getPosition()), blankToNull(profile.getCity()),
            named ? contact(profile) : List.of(), blankToNull(profile.getBio()), skills,
            profile.getLanguages() == null ? List.of() : profile.getLanguages(), trainings.validCertifications(freelanceId), missions);
    }

    @Transactional(readOnly = true)
    public ActivityReportService.PdfFile pdf(Long userId, Long freelanceId, DossierLayout layout, DocumentLanguage language) {
        DossierDto dossier = dossier(userId, freelanceId, layout);
        byte[] content = renderer.render(dossier, language, LocalDate.now(clock.withZone(PARIS)));
        String prefix = language == DocumentLanguage.EN ? "skills-profile" : "dossier-competences";
        return new ActivityReportService.PdfFile(prefix + "-" + fileWord(dossier.name()) + ".pdf", content);
    }

    private static String fullName(UserDto profile) {
        return Stream.of(profile.getFirstName(), profile.getLastName()).filter(Objects::nonNull).map(String::strip)
            .filter(part -> !part.isEmpty()).reduce((first, last) -> first + " " + last).orElse("");
    }

    /** "A. M." from Alice Martin: an anonymized dossier names no one. */
    static String initials(UserDto profile) {
        return Stream.of(profile.getFirstName(), profile.getLastName()).filter(Objects::nonNull).map(String::strip)
            .filter(part -> !part.isEmpty()).map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + ".")
            .reduce((first, last) -> first + " " + last).orElse("");
    }

    private static List<String> contact(UserDto profile) {
        return Stream.of(profile.getEmail(), profile.getPhone(), profile.getLinkedin(), profile.getWebsite())
            .map(DossierService::blankToNull).filter(Objects::nonNull).toList();
    }

    /** The description on one paragraph per line break, cut at a word with an ellipsis past {@value #DESCRIPTION_LENGTH}. */
    static String excerpt(String description) {
        String text = blankToNull(description);
        if (text == null || text.length() <= DESCRIPTION_LENGTH) {
            return text;
        }
        int cut = text.lastIndexOf(' ', DESCRIPTION_LENGTH);
        return text.substring(0, cut > DESCRIPTION_LENGTH / 2 ? cut : DESCRIPTION_LENGTH).strip() + "…";
    }

    /** The name as a file name word: "alice-martin", "a-m"; "profile" without one. */
    static String fileWord(String name) {
        String word = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return word.isEmpty() ? "profile" : word;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
