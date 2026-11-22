package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.OfferPublisherBlockDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.OfferPublisherBlock;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import dev.swirlit.indezy.repository.OfferPublisherBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The workspace's blocklist: publishers (intermediaries or clients) whose offers stay out of its feed, its saved
 * searches and its market snapshot. A name is matched case, accents and punctuation aside, as offers' publishers are.
 */
@Service
@RequiredArgsConstructor
public class OfferPublisherBlockService {

    static final int MAX_BLOCKS = 200;
    /** A publisher is offered to block once the workspace hid this many of its offers within {@link #SUGGESTION_DAYS} days. */
    static final int SUGGESTION_HIDDEN_MIN = 5;
    static final int SUGGESTION_DAYS = 30;
    private static final int SUGGESTIONS_MAX = 3;

    private final OfferPublisherBlockRepository blocks;
    private final FreelanceRepository freelances;
    private final JobOfferStateRepository states;
    private final Clock clock;

    /**
     * The publishers to offer blocking: those of which the workspace hid {@value #SUGGESTION_HIDDEN_MIN} offers or more in
     * the last {@value #SUGGESTION_DAYS} days, their names folded together, never kept nor applied to, not blocked yet;
     * the most hidden first, three at most.
     */
    @Transactional(readOnly = true)
    public List<OfferPublisherBlockDto.Suggestion> suggestions(Long freelanceId) {
        Set<String> skip = new HashSet<>();
        states.findKeptPublishers(freelanceId).forEach(name -> skip.add(JobOffer.companyKeyOf(name)));
        blocks.findByFreelanceIdOrderByNameAsc(freelanceId).forEach(block -> skip.add(block.getPublisherKey()));
        Map<String, long[]> counts = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (Object[] row : states.countHiddenByPublisher(freelanceId, LocalDateTime.now(clock).minusDays(SUGGESTION_DAYS))) {
            String name = (String) row[0];
            String key = JobOffer.companyKeyOf(name);
            if (key == null || skip.contains(key)) {
                continue;
            }
            long hidden = ((Number) row[1]).longValue();
            long[] count = counts.computeIfAbsent(key, ignored -> new long[1]);
            count[0] += hidden;
            names.merge(key, name.strip(), (kept, other) -> kept);
        }
        return counts.entrySet().stream().filter(entry -> entry.getValue()[0] >= SUGGESTION_HIDDEN_MIN)
            .sorted(Comparator.comparingLong((Map.Entry<String, long[]> entry) -> entry.getValue()[0]).reversed().thenComparing(Map.Entry::getKey))
            .limit(SUGGESTIONS_MAX).map(entry -> new OfferPublisherBlockDto.Suggestion(names.get(entry.getKey()), entry.getValue()[0])).toList();
    }

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
