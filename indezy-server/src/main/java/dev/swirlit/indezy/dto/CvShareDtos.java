package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierLayout;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;

/** CV profiles shared through a link (see docs/features.md#cv-profiles). */
public final class CvShareDtos {

    private CvShareDtos() {
    }

    /** A link to make: the profile, who it is for, named or anonymized, for how many days (7 when none). */
    public record ShareRequest(@NotNull Long profileId, @NotBlank @Size(max = 120) String recipient, DossierLayout layout,
                               @Min(1) @Max(30) Integer days) {
    }

    public record Share(Long id, Long profileId, String profileName, String recipient, DossierLayout layout, LocalDateTime createdAt,
                        Instant expiresAt, Instant revokedAt, boolean active, int openCount, int downloadCount, Instant lastOpenedAt) {
    }

    /** A link just made: its token, shown this once. */
    public record CreatedShare(Share share, String token) {

        @Override
        public String toString() {
            return "CreatedShare[share=" + (share == null ? null : share.id()) + "]";
        }
    }

    /** What the link shows: whose CV (initials when anonymized), its headline, its language, until when. */
    public record PublicShare(String name, String headline, DocumentLanguage language, DossierLayout layout, Instant expiresAt) {
    }
}
