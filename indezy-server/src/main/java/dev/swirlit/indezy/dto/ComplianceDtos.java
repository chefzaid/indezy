package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ComplianceDocumentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** The compliance vault's documents and the packs that share them, as shown, and the requests that write them. */
public final class ComplianceDtos {

    private ComplianceDtos() {
    }

    /** Whether a document is still good: past its end, within 30 days of it, before it, or without one. */
    public enum Validity { VALID, EXPIRING, EXPIRED, NO_EXPIRY }

    public record Document(Long id, ComplianceDocumentType type, String label, String fileName, String contentType, long sizeBytes,
                           LocalDate issuedOn, LocalDate expiresOn, Validity validity, LocalDateTime createdAt) {
    }

    /** What a document is: its type, a label (required for {@code OTHER}), and when it was issued and expires. */
    public record Details(@NotNull ComplianceDocumentType type, @Size(max = 100) String label, LocalDate issuedOn, LocalDate expiresOn) {
    }

    /** Documents to share with someone, for 1 to 30 days (14 when not said). */
    public record PackRequest(@NotBlank @Size(max = 120) String recipient, @NotEmpty @Size(max = 20) List<@NotNull Long> documentIds,
                              @Min(1) @Max(30) Integer days) {
        public PackRequest {
            documentIds = documentIds == null ? null : List.copyOf(documentIds);
        }
    }

    public record Pack(Long id, String recipient, List<Long> documentIds, LocalDateTime createdAt, Instant expiresAt, Instant revokedAt,
                       boolean active, int openCount, Instant lastOpenedAt) {
        public Pack {
            documentIds = List.copyOf(documentIds);
        }
    }

    /** A new pack and the token of its link, shown this once. */
    public record CreatedPack(Pack pack, String token) {
        @Override
        public String toString() {
            return "CreatedPack[pack=" + (pack == null ? null : pack.id()) + "]";
        }
    }

    /** What the link shows its recipient: who shares, until when, and the documents, without their content. */
    public record PublicPack(String sharedBy, String recipient, Instant expiresAt, List<PublicDocument> documents) {
        public PublicPack {
            documents = List.copyOf(documents);
        }
    }

    public record PublicDocument(Long id, ComplianceDocumentType type, String label, String fileName, LocalDate issuedOn,
                                 LocalDate expiresOn) {
    }
}
