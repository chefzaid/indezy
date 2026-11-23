package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** The profile's past positions and education, as shown and as written (see docs/features.md#experience-and-education). */
public final class CareerDtos {

    private CareerDtos() {
    }

    /** A past position; {@code endsOn} null while it runs. */
    public record Experience(Long id, String employer, String title, LocalDate startsOn, LocalDate endsOn, String description, String stack,
                             boolean shownPublicly) {
    }

    /** A past position as written; it stays off the public profile unless {@code shownPublicly}. */
    public record ExperienceDetails(@NotBlank @Size(max = 200) String employer, @NotBlank @Size(max = 200) String title, LocalDate startsOn,
                                    LocalDate endsOn, @Size(max = 2000) String description, @Size(max = 500) String stack,
                                    Boolean shownPublicly) {
    }

    /** A school or degree. */
    public record Education(Long id, String school, String degree, Integer startYear, Integer endYear, boolean shownPublicly) {
    }

    /** A school or degree as written; it stays off the public profile unless {@code shownPublicly}. */
    public record EducationDetails(@NotBlank @Size(max = 200) String school, @Size(max = 200) String degree,
                                   @Min(1950) @Max(2100) Integer startYear, @Min(1950) @Max(2100) Integer endYear, Boolean shownPublicly) {
    }
}
