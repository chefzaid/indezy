package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferBulkImportDto;
import dev.swirlit.indezy.dto.OfferBulkImportRequest;
import dev.swirlit.indezy.dto.OfferBulkStateRequest;
import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.dto.OfferStateUpdateRequest;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Actions on several offers of the feed at once. Each offer is handled in its own transaction, so one offer removed
 * meanwhile or already in the pipeline does not undo the others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfferBulkService {

    private final OfferStateService states;
    private final OfferImportService imports;

    /** Saves, hides or marks seen each offer and its copies; offers no longer collected are left out of the answer. */
    public List<OfferStateDto> updateAll(Long freelanceId, OfferBulkStateRequest request) {
        OfferStateUpdateRequest update = new OfferStateUpdateRequest(request.saved(), request.hidden(), request.seen());
        List<OfferStateDto> updated = new ArrayList<>();
        for (Long offerId : request.offerIds().stream().distinct().toList()) {
            try {
                updated.add(states.update(freelanceId, offerId, update));
            } catch (ResourceNotFoundException gone) {
                log.debug("Offer {} is no longer collected", offerId);
            }
        }
        return updated;
    }

    /** Adds each offer's mission to the pipeline, once: a mission already there, by any of its copies, is reported. */
    public OfferBulkImportDto importAll(Long freelanceId, OfferBulkImportRequest request) {
        List<OfferBulkImportDto.Imported> imported = new ArrayList<>();
        List<OfferBulkImportDto.Imported> already = new ArrayList<>();
        List<Long> missing = new ArrayList<>();
        for (Long offerId : request.offerIds().stream().distinct().toList()) {
            try {
                Optional<Long> project = states.pipelineProject(freelanceId, offerId);
                if (project.isPresent()) {
                    already.add(new OfferBulkImportDto.Imported(offerId, project.get()));
                } else {
                    imported.add(new OfferBulkImportDto.Imported(offerId, imports.importOffer(freelanceId, offerId).getId()));
                }
            } catch (ResourceNotFoundException gone) {
                missing.add(offerId);
            }
        }
        log.info("Workspace {} added {} offers to the pipeline ({} already there)", freelanceId, imported.size(), already.size());
        return new OfferBulkImportDto(imported, already, missing);
    }
}
