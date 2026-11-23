package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.BillingIdentityDto;
import dev.swirlit.indezy.model.DocumentFontFile;
import dev.swirlit.indezy.model.DocumentLogo;
import dev.swirlit.indezy.model.DocumentTermsFile;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.BillingIdentityService;
import dev.swirlit.indezy.service.DocumentFontService;
import dev.swirlit.indezy.service.DocumentLogoService;
import dev.swirlit.indezy.service.DocumentTermsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** The seller's identity on the signed-in workspace's invoices. */
@RestController
@RequestMapping("/users/billing")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "The seller's identity on invoices")
public class BillingController {

    private final BillingIdentityService billing;
    private final DocumentLogoService logos;
    private final DocumentTermsService termsFiles;
    private final DocumentFontService fonts;
    private final AccessGuard accessGuard;

    @Operation(summary = "The seller's identity, its defaults drawn from the profile")
    @GetMapping
    public BillingIdentityDto get() {
        return billing.get(workspace());
    }

    @Operation(summary = "Set the seller's identity")
    @PutMapping
    public BillingIdentityDto update(@Valid @RequestBody BillingIdentityDto request) {
        return billing.update(workspace(), request);
    }

    @Operation(summary = "Set the logo of the invoices and quotes to come", description = "A PNG or JPEG of 2 MB at most")
    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BillingIdentityDto uploadLogo(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.getSize() > DocumentLogo.UPLOAD_MAX_BYTES) {
            throw new IllegalArgumentException("A logo holds 2 MB at most");
        }
        return logos.upload(workspace(), file.getBytes());
    }

    @Operation(summary = "Print the invoices and quotes to come in an uploaded font",
        description = "A TrueType font of 5 MB at most whose licence allows embedding, and optionally its bold face")
    @PostMapping(value = "/font", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BillingIdentityDto uploadFont(@RequestParam("file") MultipartFile file, @RequestParam(value = "bold", required = false) MultipartFile bold)
        throws IOException {
        if (file.getSize() > DocumentFontFile.MAX_BYTES || bold != null && bold.getSize() > DocumentFontFile.MAX_BYTES) {
            throw new IllegalArgumentException("A font file holds 5 MB at most");
        }
        return fonts.upload(workspace(), file.getBytes(), bold == null || bold.isEmpty() ? null : bold.getBytes());
    }

    @Operation(summary = "Go back to a bundled font for the documents to come")
    @DeleteMapping("/font")
    public BillingIdentityDto removeFont() {
        return fonts.remove(workspace());
    }

    @Operation(summary = "Take the logo off the invoices and quotes to come")
    @DeleteMapping("/logo")
    public BillingIdentityDto removeLogo() {
        return logos.remove(workspace());
    }

    @Operation(summary = "One of the workspace's logos, as an image")
    @GetMapping("/logos/{id}")
    public ResponseEntity<byte[]> logo(@PathVariable Long id) {
        DocumentLogo logo = logos.get(workspace(), id);
        return DocumentResponses.of("logo." + (logo.getContentType().endsWith("png") ? "png" : "jpg"), logo.getContentType(), logo.getContent(),
            true);
    }

    @Operation(summary = "Set the terms and conditions PDF appended to the quotes to come", description = "5 MB and 20 pages at most")
    @PostMapping(value = "/quote-terms-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BillingIdentityDto uploadTermsFile(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.getSize() > DocumentTermsFile.MAX_BYTES) {
            throw new IllegalArgumentException("Terms and conditions hold 5 MB at most");
        }
        return termsFiles.upload(workspace(), file.getOriginalFilename(), file.getBytes());
    }

    @Operation(summary = "Stop appending the terms and conditions PDF to the quotes to come")
    @DeleteMapping("/quote-terms-file")
    public BillingIdentityDto removeTermsFile() {
        return termsFiles.remove(workspace());
    }

    @Operation(summary = "One of the workspace's terms and conditions PDFs")
    @GetMapping("/quote-terms-files/{id}")
    public ResponseEntity<byte[]> termsFile(@PathVariable Long id) {
        DocumentTermsFile file = termsFiles.get(workspace(), id);
        return DocumentResponses.of(file.getFileName(), "application/pdf", file.getContent(), true);
    }

    private Long workspace() {
        return accessGuard.currentFreelanceId().orElseThrow(() -> new AccessDeniedException("No workspace for this account"));
    }
}
