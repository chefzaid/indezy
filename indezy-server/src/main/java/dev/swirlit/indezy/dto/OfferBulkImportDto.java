package dev.swirlit.indezy.dto;

import java.util.List;

/**
 * What adding several offers to the pipeline did: the opportunities created, the offers whose mission already was in
 * the pipeline (with its opportunity), and the offers no longer collected.
 */
public record OfferBulkImportDto(List<Imported> imported, List<Imported> alreadyInPipeline, List<Long> missing) {

    public OfferBulkImportDto {
        imported = List.copyOf(imported);
        alreadyInPipeline = List.copyOf(alreadyInPipeline);
        missing = List.copyOf(missing);
    }

    /** An offer and the opportunity of its mission. */
    public record Imported(Long offerId, Long projectId) {
    }
}
