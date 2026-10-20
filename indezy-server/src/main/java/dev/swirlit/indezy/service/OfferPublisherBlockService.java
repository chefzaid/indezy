package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.OfferPublisherBlockDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferPublisherBlock;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.OfferPublisherBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The workspace's blocklist: publishers (intermediaries or clients) whose offers stay out of its feed, its saved
 * searches and its market snapshot. A name is matched case, accents and punctuation aside, as offers' publishers are.
 */
@Service
@RequiredArgsConstructor
public class OfferPublisherBlockService {

    static final int MAX_BLOCKS = 200;

    private final OfferPublisherBlockRepository blocks;
    private final FreelanceRepository freelances;

    /** The workspace's blocked publishers, by name; also its part of the account data export. */
    @Transactional(readOnly = true)
    public List<OfferPublisherBlockDto> list(Long freelanceId) {
        return blocks.findByFreelanceIdOrderByNameAsc(freelanceId).stream().map(OfferPublisherBlockService::toDto).toList();
    }

    /** Blocks a publisher once: a name read the same as one already blocked answers that block. */
    @Transactional
    public OfferPublisherBlockDto block(Long freelanceId, String name) {
        String key = JobOffer.companyKeyOf(name);
        if (key == null) {
            throw new IllegalArgumentException(ErrorMessages.OFFER_PUBLISHER_NAME_REQUIRED);
        }
        return blocks.findByFreelanceIdAndPublisherKey(freelanceId, key).map(OfferPublisherBlockService::toDto).orElseGet(() -> {
            if (blocks.countByFreelanceId(freelanceId) >= MAX_BLOCKS) {
                throw new InvalidStateException(String.format(ErrorMessages.OFFER_PUBLISHER_BLOCKS_LIMIT, MAX_BLOCKS));
            }
            OfferPublisherBlock block = new OfferPublisherBlock();
            block.setFreelance(freelances.getReferenceById(freelanceId));
            block.setName(name.strip());
            block.setPublisherKey(key);
            return toDto(blocks.save(block));
        });
    }

    @Transactional
    public void unblock(Long freelanceId, Long id) {
        OfferPublisherBlock block = blocks.findByIdAndFreelanceId(id, freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.OFFER_PUBLISHER_BLOCK_NOT_FOUND, id)));
        blocks.delete(block);
    }

    private static OfferPublisherBlockDto toDto(OfferPublisherBlock block) {
        return new OfferPublisherBlockDto(block.getId(), block.getName());
    }
}
