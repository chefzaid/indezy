package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.DocumentTemplateKind;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A Word template kept for the invoices or the quotes: its file, the placeholders it uses, those unknown, and the
 * mentions it lacks, which the filled documents add at their end.
 */
public record DocumentTemplateDto(DocumentTemplateKind kind, String fileName, LocalDateTime uploadedAt, List<String> found, List<String> unknown,
                                  List<String> missing) {

    public DocumentTemplateDto {
        found = List.copyOf(found);
        unknown = List.copyOf(unknown);
        missing = List.copyOf(missing);
    }
}
