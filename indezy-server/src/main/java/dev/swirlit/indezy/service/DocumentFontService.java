package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.BillingIdentityDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.DocumentFontFile;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.enums.DocumentFont;
import dev.swirlit.indezy.pdf.FontCheck;
import dev.swirlit.indezy.repository.DocumentFontFileRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * The font uploaded for a workspace's invoices and quotes (see docs/features.md#billing-identity): a TrueType regular
 * face of 5 MB at most and, optionally, its bold, each checked by {@link FontCheck}. Each upload is a new file, so the
 * documents issued with the previous one keep it; uploading one chooses it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentFontService {

    private final FreelanceRepository freelances;
    private final DocumentFontFileRepository fonts;

    @Transactional
    public BillingIdentityDto upload(Long freelanceId, byte[] regular, byte[] bold) {
        for (byte[] face : bold == null ? new byte[][] {regular} : new byte[][] {regular, bold}) {
            if (face.length == 0 || face.length > DocumentFontFile.MAX_BYTES) {
                throw new IllegalArgumentException("A font file holds 5 MB at most");
            }
        }
        String family = FontCheck.family(regular);
        if (bold != null) {
            FontCheck.family(bold);
        }
        Freelance freelance = freelances.findById(freelanceId).orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        DocumentFontFile file = new DocumentFontFile();
        file.setFreelanceId(freelanceId);
        file.setFamily(family.length() > 100 ? family.substring(0, 100) : family);
        file.setRegular(regular);
        file.setBold(bold);
        DocumentFontFile saved = fonts.save(file);
        BillingIdentity billing = Objects.requireNonNullElseGet(freelance.getBilling(), BillingIdentity::new);
        billing.setFontFileId(saved.getId());
        billing.setFontFamily(saved.getFamily());
        billing.setFont(DocumentFont.CUSTOM);
        freelance.setBilling(billing);
        log.info("Workspace {} set document font {} ({})", freelanceId, saved.getId(), saved.getFamily());
        return BillingIdentityService.toDto(freelance);
    }

    /** Stops using the font for the documents to come, back to the sans-serif one; those issued with it keep it. */
    @Transactional
    public BillingIdentityDto remove(Long freelanceId) {
        Freelance freelance = freelances.findById(freelanceId).orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
        BillingIdentity billing = freelance.getBilling();
        if (billing != null) {
            billing.setFontFileId(null);
            billing.setFontFamily(null);
            if (billing.getFont() == DocumentFont.CUSTOM) {
                billing.setFont(DocumentFont.SANS);
            }
        }
        return BillingIdentityService.toDto(freelance);
    }
}
