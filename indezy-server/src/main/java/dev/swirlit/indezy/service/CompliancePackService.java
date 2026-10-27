package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ComplianceDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.CompliancePack;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.ComplianceDocumentRepository;
import dev.swirlit.indezy.repository.CompliancePackRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Packs of compliance documents shared through one link (see docs/features.md#compliance-documents): the link's token
 * is shown once and only its SHA-256 is kept; it opens the pack until it expires (1 to 30 days) or is revoked, each
 * opening counted, and serves only the documents the pack names that are still in the vault.
 */
@Service
@RequiredArgsConstructor
public class CompliancePackService {

    static final int DEFAULT_DAYS = 14;
    static final int MAX_ACTIVE = 20;
    private static final int TOKEN_BYTES = 32;

    private final CompliancePackRepository packs;
    private final ComplianceDocumentRepository documents;
    private final FreelanceRepository freelances;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public List<ComplianceDtos.Pack> list(Long freelanceId) {
        return packs.findByFreelanceIdOrderByCreatedAtDesc(freelanceId).stream().map(this::toDto).toList();
    }

    @Transactional
    public ComplianceDtos.CreatedPack create(Long freelanceId, ComplianceDtos.PackRequest request) {
        Set<Long> ids = new LinkedHashSet<>(request.documentIds());
        if (documents.findSummaries(freelanceId, ids).size() != ids.size()) {
            throw new ResourceNotFoundException("Document not found");
        }
        if (list(freelanceId).stream().filter(ComplianceDtos.Pack::active).count() >= MAX_ACTIVE) {
            throw new InvalidStateException("Up to " + MAX_ACTIVE + " links can be open at once: revoke one first");
        }
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        CompliancePack pack = new CompliancePack();
        pack.setFreelanceId(freelanceId);
        pack.setRecipient(request.recipient().strip());
        pack.setTokenHash(ActivityReportService.hash(token));
        pack.setExpiresAt(clock.instant().plus(Duration.ofDays(request.days() == null ? DEFAULT_DAYS : request.days())));
        pack.setDocumentIds(ids);
        return new ComplianceDtos.CreatedPack(toDto(packs.save(pack)), token);
    }

    /** Closes a pack's link for good; a revoked pack stays listed. */
    @Transactional
    public ComplianceDtos.Pack revoke(Long freelanceId, Long id) {
        CompliancePack pack = packs.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Pack not found"));
        if (pack.getRevokedAt() == null) {
            pack.setRevokedAt(clock.instant());
        }
        return toDto(pack);
    }

    /** What the link shows, counted as an opening. */
    @Transactional
    public ComplianceDtos.PublicPack open(String token) {
        CompliancePack pack = valid(token);
        pack.setOpenCount(pack.getOpenCount() + 1);
        pack.setLastOpenedAt(clock.instant());
        List<ComplianceDtos.PublicDocument> shared = documents.findSummaries(pack.getFreelanceId(), pack.getDocumentIds()).stream()
            .map(summary -> new ComplianceDtos.PublicDocument(summary.getId(), summary.getType(), summary.getLabel(), summary.getFileName(),
                summary.getIssuedOn(), summary.getExpiresOn()))
            .toList();
        String sharedBy = freelances.findById(pack.getFreelanceId()).map(CompliancePackService::name).orElse("");
        return new ComplianceDtos.PublicPack(sharedBy, pack.getRecipient(), pack.getExpiresAt(), shared);
    }

    /** One of the pack's documents, through its link. */
    @Transactional(readOnly = true)
    public ComplianceDocumentService.StoredFile document(String token, Long documentId) {
        CompliancePack pack = valid(token);
        if (!pack.getDocumentIds().contains(documentId)) {
            throw new ResourceNotFoundException("Document not found");
        }
        return documents.findByIdAndFreelanceId(documentId, pack.getFreelanceId()).map(ComplianceDocumentService.StoredFile::of)
            .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    /** A pack whose link still opens; any other token reads as not found, whatever the reason. */
    private CompliancePack valid(String token) {
        if (token == null || token.isBlank()) {
            throw new ResourceNotFoundException("Link invalid or expired");
        }
        return packs.findByTokenHash(ActivityReportService.hash(token.strip()))
            .filter(pack -> pack.getRevokedAt() == null && pack.getExpiresAt().isAfter(clock.instant()))
            .orElseThrow(() -> new ResourceNotFoundException("Link invalid or expired"));
    }

    private ComplianceDtos.Pack toDto(CompliancePack pack) {
        boolean active = pack.getRevokedAt() == null && pack.getExpiresAt().isAfter(clock.instant());
        return new ComplianceDtos.Pack(pack.getId(), pack.getRecipient(), pack.getDocumentIds().stream().sorted().toList(), pack.getCreatedAt(),
            pack.getExpiresAt(), pack.getRevokedAt(), active, pack.getOpenCount(), pack.getLastOpenedAt());
    }

    /** The billing identity's legal name, or else the workspace's name. */
    private static String name(Freelance freelance) {
        if (freelance.getBilling() != null && freelance.getBilling().getLegalName() != null) {
            return freelance.getBilling().getLegalName();
        }
        return Stream.of(freelance.getFirstName(), freelance.getLastName()).filter(part -> part != null && !part.isBlank())
            .collect(Collectors.joining(" "));
    }
}
