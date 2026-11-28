package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.DossierLayout;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A CV profile shared through a link of its own for a few days (see docs/features.md#cv-profiles): viewed and
 * downloaded without an account, as a PDF or a Word document, named or anonymized as chosen. Only the SHA-256 of the
 * link's token is kept; the freelancer can revoke the link, and each opening and download is counted.
 */
@Entity
@Table(name = "cv_shares")
@Getter
@Setter
public class CvShare extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    /** Who shared it: whose profile the dossier is drawn from. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    /** Who the link is for, as the freelancer names them (an ESN, a recruiter). */
    @Column(name = "recipient", nullable = false, length = 120)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "layout", nullable = false, length = 12)
    private DossierLayout layout = DossierLayout.NAMED;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "open_count", nullable = false)
    private int openCount;

    @Column(name = "download_count", nullable = false)
    private int downloadCount;

    @Column(name = "last_opened_at")
    private Instant lastOpenedAt;
}
