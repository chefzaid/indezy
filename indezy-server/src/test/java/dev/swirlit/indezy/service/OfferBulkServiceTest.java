package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferBulkImportDto;
import dev.swirlit.indezy.dto.OfferBulkImportRequest;
import dev.swirlit.indezy.dto.OfferBulkStateRequest;
import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.dto.OfferStateUpdateRequest;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferBulkServiceTest {

    private final OfferStateService states = mock(OfferStateService.class);
    private final OfferImportService imports = mock(OfferImportService.class);
    private final OfferBulkService service = new OfferBulkService(states, imports);

    @Test
    void changesEachOfferOnceAndLeavesOutTheOnesNoLongerCollected() {
        OfferStateUpdateRequest hide = new OfferStateUpdateRequest(null, true, null);
        when(states.update(3L, 7L, hide)).thenReturn(new OfferStateDto(7L, false, true, false, null));
        when(states.update(3L, 8L, hide)).thenThrow(new ResourceNotFoundException("Job offer not found with id: 8"));
        when(states.update(3L, 9L, hide)).thenReturn(new OfferStateDto(9L, false, true, true, null));

        List<OfferStateDto> updated = service.updateAll(3L, new OfferBulkStateRequest(List.of(7L, 8L, 9L, 7L), null, true, null));

        assertThat(updated).extracting(OfferStateDto::offerId).containsExactly(7L, 9L);
        verify(states, times(1)).update(3L, 7L, hide);
    }

    @Test
    void addsEachMissionToThePipelineOnce() {
        when(states.pipelineProject(3L, 7L)).thenReturn(Optional.empty());
        when(states.pipelineProject(3L, 8L)).thenReturn(Optional.of(40L));
        when(states.pipelineProject(3L, 9L)).thenThrow(new ResourceNotFoundException("Job offer not found with id: 9"));
        ProjectDto created = new ProjectDto();
        created.setId(41L);
        when(imports.importOffer(3L, 7L)).thenReturn(created);

        OfferBulkImportDto result = service.importAll(3L, new OfferBulkImportRequest(List.of(7L, 8L, 9L, 8L)));

        assertThat(result.imported()).containsExactly(new OfferBulkImportDto.Imported(7L, 41L));
        assertThat(result.alreadyInPipeline()).containsExactly(new OfferBulkImportDto.Imported(8L, 40L));
        assertThat(result.missing()).containsExactly(9L);
        verify(imports, never()).importOffer(eq(3L), eq(8L));
        verify(imports, times(1)).importOffer(any(), any());
    }
}
