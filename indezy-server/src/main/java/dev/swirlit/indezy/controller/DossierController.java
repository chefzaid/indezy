package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierLayout;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.DossierService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in account's dossier de compétences. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Dossier de compétences", description = "The profile, certifications and won missions as a PDF")
public class DossierController {

    private final DossierService dossiers;
    private final AccessGuard accessGuard;

    @Operation(summary = "The dossier de compétences as a PDF", description = "Named or anonymized, in French or English")
    @GetMapping("/users/dossier")
    public ResponseEntity<byte[]> pdf(@RequestParam(defaultValue = "NAMED") DossierLayout layout,
                                      @RequestParam(defaultValue = "FR") DocumentLanguage language) {
        Long freelanceId = accessGuard.requireWorkspace(null);
        return PdfResponses.attachment(dossiers.pdf(SecurityUtils.getCurrentUserId(), freelanceId, layout, language));
    }
}
