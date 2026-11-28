package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.ai.CvProfileTranslationAiService;
import dev.swirlit.indezy.dto.CvProfileDtos;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierFormat;
import dev.swirlit.indezy.model.enums.DossierLayout;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.CvProfileService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

import java.util.List;

/** The workspace's CV profiles. Another workspace's profile answers 404. */
@RestController
@RequiredArgsConstructor
@Tag(name = "CV profiles", description = "Named positionings of the profile, each drawn as its own dossier de compétences")
public class CvProfileController {

    private final CvProfileService profiles;
    private final AccessGuard accessGuard;
    private final CvProfileTranslationAiService translations;

    @Operation(summary = "The CV profiles, the oldest first")
    @GetMapping("/users/cv-profiles")
    public List<CvProfileDtos.Profile> list() {
        return profiles.list(accessGuard.requireWorkspace(null));
    }

    @Operation(summary = "What a CV profile is written from: the profile's headline, summary, skills, won missions and past positions")
    @GetMapping("/users/cv-profiles/sources")
    public CvProfileDtos.Sources sources() {
        return profiles.sources(SecurityUtils.getCurrentUserId(), accessGuard.requireWorkspace(null));
    }

    @Operation(summary = "Add a CV profile", description = "20 at most; the first is the default")
    @PostMapping("/users/cv-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public CvProfileDtos.Profile create(@Valid @RequestBody CvProfileDtos.Details details) {
        return profiles.create(accessGuard.requireWorkspace(null), details);
    }

    @Operation(summary = "Change a CV profile")
    @PutMapping("/users/cv-profiles/{id}")
    public CvProfileDtos.Profile update(@PathVariable Long id, @Valid @RequestBody CvProfileDtos.Details details) {
        return profiles.update(accessGuard.requireWorkspace(null), id, details);
    }

    @Operation(summary = "Make a CV profile the default, drawn wherever none is chosen")
    @PutMapping("/users/cv-profiles/{id}/default")
    public CvProfileDtos.Profile makeDefault(@PathVariable Long id) {
        return profiles.makeDefault(accessGuard.requireWorkspace(null), id);
    }

    @Operation(summary = "Write the CV profile's twin in the other language with AI",
        description = "Its headline, summary and each shown mission's and position's wording translated; the twin is created or rewritten, " +
            "then each is edited on its own. Asked in the twin's language, a profile's dossier is the twin's")
    @PostMapping("/users/cv-profiles/{id}/translate")
    public CvProfileDtos.Profile translate(@PathVariable Long id) {
        return translations.translate(SecurityUtils.getCurrentUserId(), accessGuard.requireWorkspace(null), id);
    }

    @Operation(summary = "Delete a CV profile", description = "When it was the default, the oldest one left becomes it")
    @DeleteMapping("/users/cv-profiles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        profiles.delete(accessGuard.requireWorkspace(null), id);
    }

    @Operation(summary = "A CV profile's dossier de compétences, as a PDF or an editable Word document",
        description = "Named or anonymized; in the profile's language unless another is asked; format=DOCX for a .docx file")
    @GetMapping("/users/cv-profiles/{id}/dossier")
    public ResponseEntity<byte[]> dossier(@PathVariable Long id, @RequestParam(defaultValue = "NAMED") DossierLayout layout,
                                          @RequestParam(required = false) DocumentLanguage language,
                                          @RequestParam(defaultValue = "PDF") DossierFormat format) {
        Long freelanceId = accessGuard.requireWorkspace(null);
        return PdfResponses.document(profiles.file(SecurityUtils.getCurrentUserId(), freelanceId, id, layout, language, format));
    }
}
