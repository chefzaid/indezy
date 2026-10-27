package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.SkillLevel;

import java.time.LocalDate;
import java.util.List;

/**
 * What anyone with the link sees of a freelancer: who they are, what they do, their skills and when they are free.
 * No email, phone, address or workspace record.
 */
public record PublicProfileDto(
    String firstName,
    String lastName,
    String position,
    String city,
    String bio,
    String avatar,
    List<Skill> skills,
    List<String> languages,
    String website,
    String linkedin,
    String github,
    LocalDate availableFrom,
    boolean availableNow) {

    public PublicProfileDto {
        skills = List.copyOf(skills);
        languages = List.copyOf(languages);
    }

    public record Skill(String name, SkillLevel level, Integer years) {
    }

    // Spring MVC logs bodies through toString at DEBUG: nothing about the person (see docs/security.md).
    @Override
    public String toString() {
        return "PublicProfileDto[skills=" + skills.size() + "]";
    }
}
