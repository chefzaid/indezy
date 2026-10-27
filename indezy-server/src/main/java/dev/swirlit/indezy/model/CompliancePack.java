package dev.swirlit.indezy.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Documents shared with an ESN through one link, for a few days. Only the SHA-256 of the link's token is kept; the
 * freelancer can revoke the link, and each opening is counted.
 */
@Entity
@Table(name = "compliance_packs")
@Getter
@Setter
public class CompliancePack extends BaseEntity {

    @Column(name = "freelance_id", nullable = false)
    private Long freelanceId;

    /** Who the pack is for, as the freelancer names them (an ESN, a recruiter). */
    @Column(name = "recipient", nullable = false, length = 120)
    private String recipient;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "open_count", nullable = false)
    private int openCount;

    @Column(name = "last_opened_at")
    private Instant lastOpenedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "compliance_pack_documents", joinColumns = @JoinColumn(name = "pack_id"))
    @Column(name = "document_id", nullable = false)
    private Set<Long> documentIds = new LinkedHashSet<>();

    public Set<Long> getDocumentIds() {
        return Set.copyOf(documentIds);
    }

    public void setDocumentIds(Set<Long> documentIds) {
        this.documentIds = new LinkedHashSet<>(documentIds);
    }

    /** Forgets a document deleted from the vault. */
    public void forget(Long documentId) {
        documentIds.remove(documentId);
    }
}
