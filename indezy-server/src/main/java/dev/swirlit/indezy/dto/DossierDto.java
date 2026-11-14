package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.SkillLevel;

import java.time.LocalDate;
import java.util.List;

/**
 * What a dossier de compétences shows (see docs/features.md#dossier-de-competences): its author (initials only when
 * anonymized, {@code contact} then empty), their summary, skills, languages and valid certifications, and the missions
 * they won, the latest first, each without its client when anonymized.
 */
public record DossierDto(String name, String position, String city, List<String> contact, String summary, List<Skill> skills,
                         List<String> languages, List<String> certifications, List<Mission> missions) {

    public DossierDto {
        contact = List.copyOf(contact);
        skills = List.copyOf(skills);
        languages = List.copyOf(languages);
        certifications = List.copyOf(certifications);
        missions = List.copyOf(missions);
    }

    /** A skill of the profile, with its level and years when set. */
    public record Skill(String name, SkillLevel level, Integer years) {
    }

    /** A won mission: {@code endsOn} empty while it runs open-ended, {@code client} empty when anonymized. */
    public record Mission(String role, String client, LocalDate startsOn, LocalDate endsOn, String stack, String description) {
    }

    // Spring MVC logs bodies through toString at DEBUG: nothing about the person (see docs/security.md).
    @Override
    public String toString() {
        return "DossierDto[missions=" + missions.size() + "]";
    }
}
