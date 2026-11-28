package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.CvProfile;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** CV profiles (see docs/features.md#cv-profiles): what a profile holds, what it is written from. */
public final class CvProfileDtos {

    public static final int SKILLS_MAX = 60;
    public static final int ITEMS_MAX = 40;
    public static final int TEXT_MAX = 2_000;
    public static final int SUMMARY_MAX = 900;

    private CvProfileDtos() {
    }

    /** A won mission or a past position a profile shows, by its id, with its own wording or none (the profile's). */
    public record Item(@NotNull Long id, @Size(max = TEXT_MAX) String text) {
    }

    /** What a profile is set to; a list left null shows all of the profile's, in its order. */
    public record Details(@NotBlank @Size(max = CvProfile.NAME_MAX) String name, DocumentLanguage language, @Size(max = 120) String headline,
                          @Size(max = SUMMARY_MAX) String summary, @Size(max = SKILLS_MAX) List<@NotBlank @Size(max = 60) String> skills,
                          @Size(max = ITEMS_MAX) List<@Valid @NotNull Item> missions, @Size(max = ITEMS_MAX) List<@Valid @NotNull Item> positions) {

        public Details {
            skills = skills == null ? null : List.copyOf(skills.stream().filter(Objects::nonNull).toList());
            missions = missions == null ? null : List.copyOf(missions.stream().filter(Objects::nonNull).toList());
            positions = positions == null ? null : List.copyOf(positions.stream().filter(Objects::nonNull).toList());
        }
    }

    /** A CV profile; {@code twinId}: the same profile in the other language, or none. */
    public record Profile(Long id, String name, boolean isDefault, DocumentLanguage language, String headline, String summary, List<String> skills,
                          List<Item> missions, List<Item> positions, LocalDateTime updatedAt, Long twinId) {

        public Profile {
            skills = skills == null ? null : List.copyOf(skills);
            missions = missions == null ? null : List.copyOf(missions);
            positions = positions == null ? null : List.copyOf(positions);
        }
    }

    /** A won mission or past position of the profile, to pick and word in a CV profile. */
    public record Source(Long id, String title, String description) {
    }

    /** What a CV profile is written from: the profile's headline, summary, skills, won missions and past positions. */
    public record Sources(String headline, String summary, List<String> skills, List<Source> missions, List<Source> positions) {

        public Sources {
            skills = List.copyOf(skills);
            missions = List.copyOf(missions);
            positions = List.copyOf(positions);
        }
    }

    /** A block of a profile's wording, by its key ("headline", "summary", "mission-10", "position-20"). */
    public record Block(String key, String text) {
    }

    /** What a profile reads as, block by block, to translate into {@code language}. */
    public record Wording(Long profileId, DocumentLanguage language, List<Block> blocks) {

        public Wording {
            blocks = List.copyOf(blocks);
        }
    }
}
