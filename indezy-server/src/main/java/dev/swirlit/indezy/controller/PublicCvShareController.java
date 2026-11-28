package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.CvShareDtos;
import dev.swirlit.indezy.model.enums.DossierFormat;
import dev.swirlit.indezy.service.CvShareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a shared CV link shows, without an account. The token travels in a header, never in the address, so it stays out
 * of server and proxy logs; an unknown, expired or revoked token answers 404.
 */
@RestController
@RequestMapping("/public/cv-shares")
@RequiredArgsConstructor
@Tag(name = "Shared CV profiles", description = "A CV profile a freelancer shares through a link")
public class PublicCvShareController {

    static final String TOKEN_HEADER = "X-Cv-Token";
    private static final String TOKEN_DESCRIPTION = "The token after # in the shared link";

    private final CvShareService shares;

    @Operation(summary = "Whose CV the link shares, its headline and language, until when")
    @GetMapping
    public ResponseEntity<CvShareDtos.PublicShare> open(@Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(TOKEN_HEADER) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(shares.open(token));
    }

    @Operation(summary = "The shared CV, as a PDF or an editable Word document")
    @GetMapping("/dossier")
    public ResponseEntity<byte[]> dossier(@RequestParam(defaultValue = "PDF") DossierFormat format,
                                          @Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(TOKEN_HEADER) String token) {
        return PdfResponses.document(shares.file(token, format));
    }
}
