package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DocumentTemplateDto;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.media.MalwareScanner;
import dev.swirlit.indezy.model.DocumentTemplate;
import dev.swirlit.indezy.model.enums.DocumentTemplateKind;
import dev.swirlit.indezy.pdf.DocxTemplate;
import dev.swirlit.indezy.repository.DocumentTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The Word templates of a workspace's invoices and quotes (see docs/features.md#invoices): a {@code .docx} of 5 MB at
 * most, without macros, checked for malware and read for its placeholders, one per kind.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentTemplateService {

    private final DocumentTemplateRepository templates;
    private final MalwareScanner scanner;

    @Transactional
    public DocumentTemplateDto upload(Long freelanceId, DocumentTemplateKind kind, String originalName, byte[] bytes) {
        if (bytes.length == 0 || bytes.length > DocumentTemplate.MAX_BYTES) {
            throw new FieldRejectedException("file", "A Word template holds 5 MB at most");
        }
        DocxTemplate.Analysis analysis;
        try {
            analysis = DocxTemplate.analyse(bytes);
        } catch (IllegalArgumentException e) {
            throw new FieldRejectedException("file", e.getMessage());
        }
        scanner.scan(bytes).ifPresent(signature -> {
            log.warn("Workspace {} uploaded a template holding {}: refused", freelanceId, signature);
            throw new FieldRejectedException("file", MalwareScanner.refusal(signature));
        });
        DocumentTemplate template = templates.findByFreelanceIdAndKind(freelanceId, kind).orElseGet(DocumentTemplate::new);
        template.setFreelanceId(freelanceId);
        template.setKind(kind);
        template.setFileName(fileName(originalName));
        template.setContent(bytes);
        DocumentTemplate saved = templates.saveAndFlush(template);
        log.info("Workspace {} set its {} template ({} placeholders, {} unknown)", freelanceId, kind, analysis.found().size(),
            analysis.unknown().size());
        return dto(saved, analysis);
    }

    @Transactional(readOnly = true)
    public List<DocumentTemplateDto> list(Long freelanceId) {
        return templates.findByFreelanceId(freelanceId).stream().sorted(Comparator.comparing(DocumentTemplate::getKind))
            .map(template -> dto(template, DocxTemplate.analyse(template.getContent()))).toList();
    }

    /** The template of a kind, as uploaded; none when there is none. */
    @Transactional(readOnly = true)
    public Optional<byte[]> content(Long freelanceId, DocumentTemplateKind kind) {
        return templates.findByFreelanceIdAndKind(freelanceId, kind).map(DocumentTemplate::getContent);
    }

    @Transactional
    public void remove(Long freelanceId, DocumentTemplateKind kind) {
        templates.findByFreelanceIdAndKind(freelanceId, kind).ifPresent(templates::delete);
    }

    private static DocumentTemplateDto dto(DocumentTemplate template, DocxTemplate.Analysis analysis) {
        return new DocumentTemplateDto(template.getKind(), template.getFileName(),
            template.getUpdatedAt() != null ? template.getUpdatedAt() : template.getCreatedAt(), analysis.found(), analysis.unknown(), analysis.missing());
    }

    private static String fileName(String original) {
        if (original == null || original.isBlank()) {
            return "modele.docx";
        }
        String path = original.replace('\\', '/');
        String name = path.substring(path.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").strip();
        return name.isEmpty() ? "modele.docx" : name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
