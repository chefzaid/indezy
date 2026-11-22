package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.BillingIdentityDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.media.MalwareScanner;
import dev.swirlit.indezy.media.NetworkDocuments;
import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.DocumentTermsFile;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.DocumentTermsFileRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Terms and conditions of sale as a PDF, appended after each quote (see docs/features.md#quotes): a PDF of 5 MB and
 * {@value DocumentTermsFile#MAX_PAGES} pages at most, checked for malware, opened without a password. Each upload is a
 * new file, so quotes sent with the previous one keep appending it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentTermsService {

    private final FreelanceRepository freelances;
    private final DocumentTermsFileRepository files;
    private final MalwareScanner scanner;

    @Transactional
    public BillingIdentityDto upload(Long freelanceId, String originalName, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > DocumentTermsFile.MAX_BYTES) {
            throw new IllegalArgumentException("Terms and conditions hold 5 MB at most");
        }
        String name = fileName(originalName);
        NetworkDocuments.Type type;
        try {
            type = NetworkDocuments.detect(name, bytes);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Send the terms and conditions as a PDF", e);
        }
        if (!"pdf".equals(type.extension())) {
            throw new IllegalArgumentException("Send the terms and conditions as a PDF");
        }
        scanner.scan(bytes).ifPresent(signature -> {
            log.warn("Workspace {} uploaded terms holding {}: refused", freelanceId, signature);
            throw new IllegalArgumentException(MalwareScanner.refusal(signature));
        });
        int pages;
        try (PDDocument document = Loader.loadPDF(bytes)) {
            pages = document.getNumberOfPages();
        } catch (IOException e) {
            throw new IllegalArgumentException("This PDF cannot be opened: send it without a password", e);
        }
        if (pages == 0 || pages > DocumentTermsFile.MAX_PAGES) {
            throw new IllegalArgumentException("Terms and conditions run " + DocumentTermsFile.MAX_PAGES + " pages at most");
        }
        Freelance freelance = freelance(freelanceId);
        DocumentTermsFile file = new DocumentTermsFile();
        file.setFreelanceId(freelanceId);
        file.setFileName(name.isBlank() ? "cgv.pdf" : name.length() > 200 ? name.substring(name.length() - 200) : name);
        file.setPages(pages);
        file.setContent(bytes);
        DocumentTermsFile saved = files.save(file);
        BillingIdentity billing = Objects.requireNonNullElseGet(freelance.getBilling(), BillingIdentity::new);
        billing.setQuoteTermsFileId(saved.getId());
        freelance.setBilling(billing);
        log.info("Workspace {} set terms file {} ({} pages)", freelanceId, saved.getId(), pages);
        return BillingIdentityService.toDto(freelance);
    }

    /** Stops appending the file to the quotes to come; those sent with it keep it. */
    @Transactional
    public BillingIdentityDto remove(Long freelanceId) {
        Freelance freelance = freelance(freelanceId);
        if (freelance.getBilling() != null) {
            freelance.getBilling().setQuoteTermsFileId(null);
        }
        return BillingIdentityService.toDto(freelance);
    }

    @Transactional(readOnly = true)
    public DocumentTermsFile get(Long freelanceId, Long id) {
        return files.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }

    /** A file by its id, for a quote that appends it; none when it is gone. */
    @Transactional(readOnly = true)
    public Optional<byte[]> content(Long id) {
        return id == null ? Optional.empty() : files.findById(id).map(DocumentTermsFile::getContent);
    }

    private static String fileName(String original) {
        if (original == null || original.isBlank()) {
            return "cgv.pdf";
        }
        String path = original.replace('\\', '/');
        String name = path.substring(path.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
        return name.isEmpty() ? "cgv.pdf" : name;
    }

    private Freelance freelance(Long freelanceId) {
        return freelances.findById(freelanceId).orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    }
}
