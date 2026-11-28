package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.CvProfile;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierStyle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
                          @Size(max = ITEMS_MAX) List<@Valid @NotNull Item> missions, @Size(max = ITEMS_MAX) List<@Valid @NotNull Item> positions,
                          DossierStyle style, @Pattern(regexp = "#[0-9A-Fa-f]{6}") String accent) {

        public Details {
            skills = skills == null ? null : List.copyOf(skills.stream().filter(Objects::nonNull).toList());
            missions = missions == null ? null : List.copyOf(missions.stream().filter(Objects::nonNull).toList());
            positions = positions == null ? null : List.copyOf(positions.stream().filter(Objects::nonNull).toList());
        }
    }

    /**
     * A CV profile; {@code twinId}: the same profile in the other language, or none; {@code changes}: what changed in the
     * profile's facts since its wording was last saved or reviewed, empty when it is in step.
     */
    public record Profile(Long id, String name, boolean isDefault, DocumentLanguage language, String headline, String summary, List<String> skills,
                          List<Item> missions, List<Item> positions, LocalDateTime updatedAt, Long twinId, boolean archived, DossierStyle style,
                          String accent, List<Change> changes) {

        public Profile {
            skills = skills == null ? null : List.copyOf(skills);
            missions = missions == null ? null : List.copyOf(missions);
            positions = positions == null ? null : List.copyOf(positions);
            changes = changes == null ? List.of() : List.copyOf(changes);
        }

        public Profile with(List<Change> found) {
            return new Profile(id, name, isDefault, language, headline, summary, skills, missions, positions, updatedAt, twinId, archived, style, accent,
                found);
        }
    }

    /** What changed in a fact a profile relies on. */
    public enum ChangeKind {
        /** A skill the profile shows is no longer in the profile. */
        SKILL_REMOVED,
        /** A skill was added to the profile, that this profile does not show. */
        SKILL_ADDED,
        CERTIFICATION_ADDED,
        CERTIFICATION_REMOVED,
        /** A mission or position the profile words had its title, dates or stack changed. */
        ITEM_CHANGED,
        /** A mission or position the profile shows is gone. */
        ITEM_REMOVED,
        /** A mission was won or a position added, that this profile does not show. */
        ITEM_ADDED
    }

    /** A change to review: its kind, what it is about ("Kafka", "Lead Java") and, for an item changed, which of its facts did. */
    public record Change(ChangeKind kind, String label, List<String> fields) {

        public Change {
            fields = fields == null ? List.of() : List.copyOf(fields);
        }
    }

    /** A mission's or position's facts a profile's wording relies on. */
    public record ItemFacts(String title, String startsOn, String endsOn, String stack) {
    }

    /** The profile's facts at one time: its skills, its certifications, and its missions' and positions' facts by key. */
    public record Facts(List<String> skills, List<String> certifications, java.util.Map<String, ItemFacts> items) {

        public Facts {
            skills = skills == null ? List.of() : List.copyOf(skills);
            certifications = certifications == null ? List.of() : List.copyOf(certifications);
            items = items == null ? java.util.Map.of() : java.util.Map.copyOf(items);
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
