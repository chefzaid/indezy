package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ComplianceDtos;
import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.ComplianceDocument;
import dev.swirlit.indezy.model.enums.ComplianceDocumentType;
import dev.swirlit.indezy.repository.ComplianceDocumentRepository;
import dev.swirlit.indezy.repository.CompliancePackRepository;
import dev.swirlit.indezy.util.AttachmentTypes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * The compliance vault (see docs/features.md#compliance-documents): up to {@value #MAX_DOCUMENTS} PDF or image
 * documents of 10 MB each, with when they were issued and expire, and the ones to renew within
 * {@value #EXPIRING_DAYS} days among the dashboard's reminders.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceDocumentService {

    /** A document's file, read within its transaction. */
    public record StoredFile(String fileName, String contentType, byte[] content) {
        public StoredFile {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }

        static StoredFile of(ComplianceDocument document) {
            return new StoredFile(document.getFileName(), document.getContentType(), document.getContent());
        }
    }

    static final int MAX_DOCUMENTS = 30;
    static final int EXPIRING_DAYS = 30;
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Set<String> TYPES = Set.of("application/pdf", "image/png", "image/jpeg");

    private final ComplianceDocumentRepository documents;
    private final CompliancePackRepository packs;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ComplianceDtos.Document> list(Long freelanceId) {
        LocalDate today = today();
        // In the order of the types (the certificates first), then the newest first.
        return documents.findSummaries(freelanceId).stream().map(summary -> toDto(summary, today))
            .sorted(Comparator.comparing((ComplianceDtos.Document document) -> document.type().ordinal())
                .thenComparing(ComplianceDtos.Document::createdAt, Comparator.reverseOrder()))
            .toList();
    }

    @Transactional
    public ComplianceDtos.Document upload(Long freelanceId, MultipartFile file, ComplianceDtos.Details details) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }
        if (file.getSize() > ComplianceDocument.MAX_BYTES) {
            throw new IllegalArgumentException("A document is 10 MB at most");
        }
        if (documents.countByFreelanceId(freelanceId) >= MAX_DOCUMENTS) {
            throw new InvalidStateException("The vault holds " + MAX_DOCUMENTS + " documents at most: delete one first");
        }
        byte[] content = file.getBytes();
        String name = ProjectAttachmentService.fileName(file.getOriginalFilename());
        AttachmentTypes.Type type = AttachmentTypes.detect(name, content).filter(found -> TYPES.contains(found.contentType()))
            .orElseThrow(() -> new IllegalArgumentException("A document is a PDF, PNG or JPEG file"));
        ComplianceDocument document = new ComplianceDocument();
        document.setFreelanceId(freelanceId);
        document.setFileName(name);
        document.setContentType(type.contentType());
        document.setSizeBytes(content.length);
        document.setContent(content);
        apply(document, details);
        ComplianceDocument saved = documents.save(document);
        log.info("Stored a {} compliance document of {} bytes in workspace {}", details.type(), content.length, freelanceId);
        return get(freelanceId, saved.getId());
    }

    @Transactional
    public ComplianceDtos.Document update(Long freelanceId, Long id, ComplianceDtos.Details details) {
        apply(document(freelanceId, id), details);
        return get(freelanceId, id);
    }

    /** Deletes a document, which the packs sharing it stop showing. */
    @Transactional
    public void delete(Long freelanceId, Long id) {
        ComplianceDocument document = document(freelanceId, id);
        packs.findSharing(id).forEach(pack -> pack.forget(id));
        documents.delete(document);
    }

    @Transactional(readOnly = true)
    public StoredFile content(Long freelanceId, Long id) {
        return StoredFile.of(document(freelanceId, id));
    }

    /** The documents expired or expiring within 30 days, the soonest first, for the dashboard's reminders. */
    @Transactional(readOnly = true)
    public List<DashboardStatsDto.ExpiringDocument> expiring(Long freelanceId) {
        LocalDate today = today();
        return documents.findExpiringBy(freelanceId, today.plusDays(EXPIRING_DAYS)).stream()
            .map(summary -> new DashboardStatsDto.ExpiringDocument(summary.getId(), summary.getType().name(), summary.getLabel(),
                summary.getExpiresOn(), ChronoUnit.DAYS.between(today, summary.getExpiresOn())))
            .toList();
    }

    private ComplianceDtos.Document get(Long freelanceId, Long id) {
        LocalDate today = today();
        return documents.findSummaries(freelanceId, List.of(id)).stream().map(summary -> toDto(summary, today)).findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    /** Sets what the document is; without an end date, a certificate's usual validity gives one from its issue date. */
    private static void apply(ComplianceDocument document, ComplianceDtos.Details details) {
        String label = details.label() == null || details.label().isBlank() ? null : details.label().strip();
        if (details.type() == ComplianceDocumentType.OTHER && label == null) {
            throw new FieldRejectedException("label", "Name the document");
        }
        LocalDate expiresOn = details.expiresOn();
        Integer months = details.type().getValidityMonths();
        if (expiresOn == null && months != null && details.issuedOn() != null) {
            expiresOn = details.issuedOn().plusMonths(months);
        }
        if (expiresOn != null && details.issuedOn() != null && expiresOn.isBefore(details.issuedOn())) {
            throw new FieldRejectedException("expiresOn", "The document expires before it was issued");
        }
        document.setType(details.type());
        document.setLabel(label);
        document.setIssuedOn(details.issuedOn());
        document.setExpiresOn(expiresOn);
    }

    static ComplianceDtos.Validity validity(LocalDate expiresOn, LocalDate today) {
        if (expiresOn == null) {
            return ComplianceDtos.Validity.NO_EXPIRY;
        }
        if (expiresOn.isBefore(today)) {
            return ComplianceDtos.Validity.EXPIRED;
        }
        return expiresOn.isAfter(today.plusDays(EXPIRING_DAYS)) ? ComplianceDtos.Validity.VALID : ComplianceDtos.Validity.EXPIRING;
    }

    private static ComplianceDtos.Document toDto(ComplianceDocumentRepository.Summary summary, LocalDate today) {
        return new ComplianceDtos.Document(summary.getId(), summary.getType(), summary.getLabel(), summary.getFileName(),
            summary.getContentType(), summary.getSizeBytes(), summary.getIssuedOn(), summary.getExpiresOn(),
            validity(summary.getExpiresOn(), today), summary.getCreatedAt());
    }

    private ComplianceDocument document(Long freelanceId, Long id) {
        return documents.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(PARIS));
    }
}
