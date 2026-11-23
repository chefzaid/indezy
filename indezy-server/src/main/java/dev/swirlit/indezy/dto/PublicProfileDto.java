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
    boolean availableNow,
    /** The certifications of the training record still valid, most recent first. */
    List<String> certifications,
    /** The testimonials the freelancer approved, the latest first. */
    List<TestimonialDtos.Shown> testimonials,
    /** The past positions the freelancer chose to show, the running and latest first. */
    List<CareerDtos.Experience> experiences,
    /** The schools the freelancer chose to show, the latest first. */
    List<CareerDtos.Education> educations) {

    public PublicProfileDto {
        skills = List.copyOf(skills);
        languages = List.copyOf(languages);
        certifications = certifications == null ? List.of() : List.copyOf(certifications);
        testimonials = testimonials == null ? List.of() : List.copyOf(testimonials);
        experiences = experiences == null ? List.of() : List.copyOf(experiences);
        educations = educations == null ? List.of() : List.copyOf(educations);
    }

    public record Skill(String name, SkillLevel level, Integer years) {
    }

    // Spring MVC logs bodies through toString at DEBUG: nothing about the person (see docs/security.md).
    @Override
    public String toString() {
        return "PublicProfileDto[skills=" + skills.size() + "]";
    }
}
