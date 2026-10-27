package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ComplianceDtos;
import dev.swirlit.indezy.model.enums.ComplianceDocumentType;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ComplianceDocumentService;
import dev.swirlit.indezy.service.CompliancePackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** The workspace's compliance documents and the links that share them. Another workspace's answer 404. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Compliance documents", description = "Certificates and papers ESNs ask for, and the links that share them")
public class ComplianceController {

    private final ComplianceDocumentService documents;
    private final CompliancePackService packs;
    private final AccessGuard accessGuard;

    @Operation(summary = "The vault's documents, without their content")
    @GetMapping("/compliance-documents")
    public List<ComplianceDtos.Document> list() {
        return documents.list(workspace());
    }

    @Operation(summary = "Store a PDF, PNG or JPEG document of up to 10 MB",
        description = "Without an end date, an URSSAF certificate expires six months after its issue and a registration extract three")
    @PostMapping(value = "/compliance-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ComplianceDtos.Document upload(@RequestParam("file") MultipartFile file, @RequestParam ComplianceDocumentType type,
                                          @RequestParam(required = false) String label,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedOn,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresOn)
        throws IOException {
        if (label != null && label.length() > 100) {
            throw new IllegalArgumentException("A label is 100 characters at most");
        }
        return documents.upload(workspace(), file, new ComplianceDtos.Details(type, label, issuedOn, expiresOn));
    }

    @Operation(summary = "Change what a document is and its dates")
    @PutMapping("/compliance-documents/{id}")
    public ComplianceDtos.Document update(@PathVariable Long id, @Valid @RequestBody ComplianceDtos.Details details) {
        return documents.update(workspace(), id, details);
    }

    @Operation(summary = "Delete a document, which the links sharing it stop showing")
    @DeleteMapping("/compliance-documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        documents.delete(workspace(), id);
    }

    @Operation(summary = "A document's content, shown in the page")
    @GetMapping("/compliance-documents/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable Long id) {
        ComplianceDocumentService.StoredFile file = documents.content(workspace(), id);
        return DocumentResponses.of(file.fileName(), file.contentType(), file.content(), true);
    }

    @Operation(summary = "The links shared, newest first, revoked and expired ones included")
    @GetMapping("/compliance-packs")
    public List<ComplianceDtos.Pack> packs() {
        return packs.list(workspace());
    }

    @Operation(summary = "Share documents through a link", description = "The token of the link is in the answer, this once only")
    @PostMapping("/compliance-packs")
    @ResponseStatus(HttpStatus.CREATED)
    public ComplianceDtos.CreatedPack share(@Valid @RequestBody ComplianceDtos.PackRequest request) {
        return packs.create(workspace(), request);
    }

    @Operation(summary = "Close a link for good")
    @PostMapping("/compliance-packs/{id}/revoke")
    public ComplianceDtos.Pack revoke(@PathVariable Long id) {
        return packs.revoke(workspace(), id);
    }

    private Long workspace() {
        return accessGuard.requireWorkspace(null);
    }
}
