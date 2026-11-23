package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.BillingIdentityDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.media.MalwareScanner;
import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.DocumentLetterhead;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.pdf.Letterhead;
import dev.swirlit.indezy.repository.DocumentLetterheadRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * The letterhead invoices and quotes are drawn over (see docs/features.md#billing-identity): a PDF of 5 MB at most, one or
 * two upright A4 pages, checked by {@link Letterhead} and for malware. Each upload is a new file, so documents issued with
 * the previous one keep it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLetterheadService {

    private final FreelanceRepository freelances;
    private final DocumentLetterheadRepository letterheads;
    private final MalwareScanner scanner;

    @Transactional
    public BillingIdentityDto upload(Long freelanceId, String originalName, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > DocumentLetterhead.MAX_BYTES) {
            throw new IllegalArgumentException("A letterhead holds 5 MB at most");
        }
        int pages = Letterhead.check(bytes);
        scanner.scan(bytes).ifPresent(signature -> {
            log.warn("Workspace {} uploaded a letterhead holding {}: refused", freelanceId, signature);
            throw new IllegalArgumentException(MalwareScanner.refusal(signature));
        });
        Freelance freelance = freelance(freelanceId);
        DocumentLetterhead letterhead = new DocumentLetterhead();
        letterhead.setFreelanceId(freelanceId);
        String name = originalName == null || originalName.isBlank() ? "papier-en-tete.pdf" :
            originalName.replace('\\', '/').substring(originalName.replace('\\', '/').lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "");
        letterhead.setFileName(name.isBlank() ? "papier-en-tete.pdf" : name.length() > 200 ? name.substring(name.length() - 200) : name);
        letterhead.setPages(pages);
        letterhead.setContent(bytes);
        DocumentLetterhead saved = letterheads.save(letterhead);
        BillingIdentity billing = Objects.requireNonNullElseGet(freelance.getBilling(), BillingIdentity::new);
        billing.setLetterheadId(saved.getId());
        freelance.setBilling(billing);
        log.info("Workspace {} set letterhead {} ({} pages)", freelanceId, saved.getId(), pages);
        return BillingIdentityService.toDto(freelance);
    }

    /** Stops drawing the letterhead under the documents to come; those issued with it keep it. */
    @Transactional
    public BillingIdentityDto remove(Long freelanceId) {
        Freelance freelance = freelance(freelanceId);
        if (freelance.getBilling() != null) {
            freelance.getBilling().setLetterheadId(null);
        }
        return BillingIdentityService.toDto(freelance);
    }

    @Transactional(readOnly = true)
    public DocumentLetterhead get(Long freelanceId, Long id) {
        return letterheads.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Letterhead not found"));
    }

    private Freelance freelance(Long freelanceId) {
        return freelances.findById(freelanceId).orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    }
}
