package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferPublisherBlockDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.OfferPublisherBlock;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.OfferPublisherBlockRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferPublisherBlockServiceTest {

    private final OfferPublisherBlockRepository blocks = mock(OfferPublisherBlockRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final OfferPublisherBlockService service = new OfferPublisherBlockService(blocks, freelances);

    @Test
    void blocksAPublisherOnceUnderTheNameItWasGiven() {
        Freelance freelance = new Freelance();
        when(freelances.getReferenceById(3L)).thenReturn(freelance);
        when(blocks.save(any())).thenAnswer(invocation -> {
            OfferPublisherBlock saved = invocation.getArgument(0);
            saved.setId(8L);
            return saved;
        });

        assertThat(service.block(3L, "  Exemple Conseil ")).isEqualTo(new OfferPublisherBlockDto(8L, "Exemple Conseil"));

        ArgumentCaptor<OfferPublisherBlock> saved = ArgumentCaptor.forClass(OfferPublisherBlock.class);
        verify(blocks).save(saved.capture());
        assertThat(saved.getValue().getPublisherKey()).isEqualTo("exemple conseil");
        assertThat(saved.getValue().getFreelance()).isSameAs(freelance);
        when(blocks.findByFreelanceIdAndPublisherKey(3L, "exemple conseil")).thenReturn(Optional.of(saved.getValue()));
        // Another spelling of the same publisher answers the same block.
        assertThat(service.block(3L, "EXEMPLE-conseil")).isEqualTo(new OfferPublisherBlockDto(8L, "Exemple Conseil"));
    }

    @Test
    void refusesANameWithoutWordsAndBlocksPastTheLimit() {
        assertThatThrownBy(() -> service.block(3L, " — ")).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("A publisher's name needs a letter or a digit");
        when(blocks.countByFreelanceId(3L)).thenReturn((long) OfferPublisherBlockService.MAX_BLOCKS);
        assertThatThrownBy(() -> service.block(3L, "Acme")).isInstanceOf(InvalidStateException.class)
            .hasMessage("A workspace blocks at most 200 publishers");
        verify(blocks, never()).save(any());
    }

    @Test
    void listsAndUnblocksTheWorkspacesPublishersOnly() {
        OfferPublisherBlock block = new OfferPublisherBlock();
        block.setId(8L);
        block.setName("Acme");
        when(blocks.findByFreelanceIdOrderByNameAsc(3L)).thenReturn(List.of(block));
        when(blocks.findByIdAndFreelanceId(8L, 3L)).thenReturn(Optional.of(block));

        assertThat(service.list(3L)).containsExactly(new OfferPublisherBlockDto(8L, "Acme"));
        service.unblock(3L, 8L);
        verify(blocks).delete(block);
        assertThatThrownBy(() -> service.unblock(4L, 8L)).isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Blocked publisher not found with id: 8");
    }
}
