package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.dto.OfferStateExportDto;
import dev.swirlit.indezy.dto.OfferStateUpdateRequest;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.JobOfferState;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** What a workspace does with offers: save, hide, mark as seen. */
@Service
@Transactional
public class OfferStateService {

    private final JobOfferRepository offers;
    private final JobOfferStateRepository states;
    private final FreelanceRepository freelances;
    private final Clock clock;

    @Autowired
    public OfferStateService(JobOfferRepository offers, JobOfferStateRepository states, FreelanceRepository freelances) {
        this(offers, states, freelances, Clock.systemUTC());
    }

    OfferStateService(JobOfferRepository offers, JobOfferStateRepository states, FreelanceRepository freelances, Clock clock) {
        this.offers = offers;
        this.states = states;
        this.freelances = freelances;
        this.clock = clock;
    }

    public OfferStateDto update(Long freelanceId, Long offerId, OfferStateUpdateRequest request) {
        JobOfferState state = stateOf(freelanceId, offerId);
        if (request.saved() != null) {
            state.setSaved(request.saved());
        }
        if (request.hidden() != null) {
            state.setHidden(request.hidden());
        }
        if (Boolean.TRUE.equals(request.seen()) && state.getSeenAt() == null) {
            state.setSeenAt(clock.instant());
        } else if (Boolean.FALSE.equals(request.seen())) {
            state.setSeenAt(null);
        }
        return toDto(states.save(state));
    }

    /** Every offer the workspace acted on, for the account data export. */
    @Transactional(readOnly = true)
    public List<OfferStateExportDto> export(Long freelanceId) {
        return states.findByFreelanceId(freelanceId).stream()
            .map(state -> new OfferStateExportDto(state.getOffer().getSite(), state.getOffer().getTitle(),
                state.getOffer().getCompany(), state.getOffer().getUrl(), state.isSaved(), state.isHidden(), state.getSeenAt(),
                state.getImportedProject() == null ? null : state.getImportedProject().getId()))
            .toList();
    }

    /** The workspace's state of an offer, created (and not yet saved) when it never acted on it. */
    public JobOfferState stateOf(Long freelanceId, Long offerId) {
        return states.findByFreelanceIdAndOfferId(freelanceId, offerId).orElseGet(() -> {
            JobOffer offer = offers.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.OFFER_NOT_FOUND, offerId)));
            Freelance freelance = freelances.findById(freelanceId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.FREELANCE_NOT_FOUND, freelanceId)));
            JobOfferState created = new JobOfferState();
            created.setOffer(offer);
            created.setFreelance(freelance);
            return created;
        });
    }

    static OfferStateDto toDto(JobOfferState state) {
        return new OfferStateDto(state.getOffer().getId(), state.isSaved(), state.isHidden(), state.getSeenAt() != null,
            state.getImportedProject() == null ? null : state.getImportedProject().getId());
    }
}
