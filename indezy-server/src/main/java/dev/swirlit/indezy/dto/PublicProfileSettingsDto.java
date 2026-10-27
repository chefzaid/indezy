package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.User;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * Whether the public profile is shown, and its address name: 3 to 40 lower-case letters, digits and single dashes,
 * starting and ending with a letter or digit.
 */
public record PublicProfileSettingsDto(
    boolean enabled,
    @Size(min = 3, max = User.PUBLIC_PROFILE_SLUG_MAX)
    @Pattern(regexp = SLUG_PATTERN, message = "Use lower-case letters, digits and dashes") String slug) {

    public static final String SLUG_PATTERN = "[a-z0-9]+(-[a-z0-9]+)*";

    // Checked as it is kept: trimmed, in lower case, and none when blank.
    public PublicProfileSettingsDto {
        slug = slug == null || slug.isBlank() ? null : slug.strip().toLowerCase(Locale.ROOT);
    }
}
