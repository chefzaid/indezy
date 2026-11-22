package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.BillingIdentityDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.media.NetworkImages;
import dev.swirlit.indezy.model.BillingIdentity;
import dev.swirlit.indezy.model.DocumentLogo;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.DocumentLogoRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * The logo on a workspace's invoices and quotes (see docs/features.md#billing-identity): a PNG or JPEG of 2 MB at most,
 * drawn anew (which drops what else it carries) at most {@value DocumentLogo#LONGEST_SIDE} pixels on its longest side.
 * Each upload is a new logo, so the documents issued with the previous one keep printing it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentLogoService {

    private final FreelanceRepository freelances;
    private final DocumentLogoRepository logos;

    @Transactional
    public BillingIdentityDto upload(Long freelanceId, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > DocumentLogo.UPLOAD_MAX_BYTES) {
            throw new IllegalArgumentException("A logo holds 2 MB at most");
        }
        NetworkImages.Image made;
        try {
            BufferedImage read = ImageIO.read(new ByteArrayInputStream(NetworkImages.process(bytes).content()));
            if (read == null) {
                throw new IllegalArgumentException("This image cannot be read: send a PNG or a JPEG");
            }
            made = NetworkImages.write(read, DocumentLogo.LONGEST_SIDE);
        } catch (IOException e) {
            throw new IllegalArgumentException("This image cannot be read: send a PNG or a JPEG", e);
        }
        Freelance freelance = freelance(freelanceId);
        DocumentLogo logo = new DocumentLogo();
        logo.setFreelanceId(freelanceId);
        logo.setContentType(made.contentType());
        logo.setContent(made.content());
        logo.setWidth(made.width());
        logo.setHeight(made.height());
        DocumentLogo saved = logos.save(logo);
        BillingIdentity billing = Objects.requireNonNullElseGet(freelance.getBilling(), BillingIdentity::new);
        billing.setLogoId(saved.getId());
        freelance.setBilling(billing);
        log.info("Workspace {} set document logo {}", freelanceId, saved.getId());
        return BillingIdentityService.toDto(freelance);
    }

    /** Takes the logo off the documents to come; those issued with it keep it. */
    @Transactional
    public BillingIdentityDto remove(Long freelanceId) {
        Freelance freelance = freelance(freelanceId);
        if (freelance.getBilling() != null) {
            freelance.getBilling().setLogoId(null);
        }
        return BillingIdentityService.toDto(freelance);
    }

    /** One of the workspace's logos, to show or print. */
    @Transactional(readOnly = true)
    public DocumentLogo get(Long freelanceId, Long id) {
        return logos.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Logo not found"));
    }

    /** A logo by its id, for a document that prints it; none when it is gone. */
    @Transactional(readOnly = true)
    public Optional<DocumentLogo> find(Long id) {
        return id == null ? Optional.empty() : logos.findById(id);
    }

    private Freelance freelance(Long freelanceId) {
        return freelances.findById(freelanceId).orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    }
}
