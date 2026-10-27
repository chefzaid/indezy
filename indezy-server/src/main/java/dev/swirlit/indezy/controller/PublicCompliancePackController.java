package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ComplianceDtos;
import dev.swirlit.indezy.service.ComplianceDocumentService;
import dev.swirlit.indezy.service.CompliancePackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a shared link shows, without an account. The token travels in a header, never in the address, so it stays out of
 * server and proxy logs; an unknown, expired or revoked token answers 404.
 */
@RestController
@RequestMapping("/public/compliance-packs")
@RequiredArgsConstructor
@Tag(name = "Shared compliance documents", description = "Documents a freelancer shares through a link")
public class PublicCompliancePackController {

    static final String TOKEN_HEADER = "X-Pack-Token";
    private static final String TOKEN_DESCRIPTION = "The token after # in the shared link";

    private final CompliancePackService packs;

    @Operation(summary = "The shared documents, without their content")
    @GetMapping
    public ResponseEntity<ComplianceDtos.PublicPack> open(@Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(TOKEN_HEADER) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(packs.open(token));
    }

    @Operation(summary = "One shared document, to download")
    @GetMapping("/documents/{id}")
    public ResponseEntity<byte[]> document(@PathVariable Long id,
                                           @Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(TOKEN_HEADER) String token) {
        ComplianceDocumentService.StoredFile file = packs.document(token, id);
        return DocumentResponses.of(file.fileName(), file.contentType(), file.content(), false);
    }
}
