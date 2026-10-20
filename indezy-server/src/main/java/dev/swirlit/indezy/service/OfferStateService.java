package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.dto.OfferStateExportDto;
import dev.swirlit.indezy.dto.OfferStateUpdateRequest;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.JobOfferState;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    /** Saves, hides, marks seen or notes an offer, and its copies on other sites with it: they are one mission. */
    public OfferStateDto update(Long freelanceId, Long offerId, OfferStateUpdateRequest request) {
        List<JobOfferState> group = groupStates(freelanceId, offerId);
        for (JobOfferState state : group) {
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
            if (request.note() != null) {
                state.setNote(request.note());
            }
        }
        return OfferStateDto.merged(offerId, states.saveAll(group).stream().map(OfferStateService::toDto).toList());
    }

    /** The workspace's state of the offer, then of each of its copies, created (not yet saved) where it never acted. */
    public List<JobOfferState> groupStates(Long freelanceId, Long offerId) {
        JobOfferState first = stateOf(freelanceId, offerId);
        List<JobOfferState> group = new ArrayList<>(List.of(first));
        for (JobOffer copy : offers.findCopyGroupOf(first.getOffer())) {
            if (!copy.getId().equals(offerId)) {
                group.add(stateOf(freelanceId, copy.getId()));
            }
        }
        return group;
    }

    /** The opportunity the workspace made of the offer's mission, by any of its copies. */
    @Transactional(readOnly = true)
    public Optional<Long> pipelineProject(Long freelanceId, Long offerId) {
        return pipelineProject(groupStates(freelanceId, offerId));
    }

    static Optional<Long> pipelineProject(List<JobOfferState> group) {
        return group.stream().map(JobOfferState::getImportedProject).filter(Objects::nonNull).findFirst().map(Project::getId);
    }

    /** Every offer the workspace acted on, for the account data export. */
    @Transactional(readOnly = true)
    public List<OfferStateExportDto> export(Long freelanceId) {
        return states.findByFreelanceId(freelanceId).stream()
            .map(state -> new OfferStateExportDto(state.getOffer().getSite(), state.getOffer().getTitle(),
                state.getOffer().getCompany(), state.getOffer().getUrl(), state.isSaved(), state.isHidden(), state.getSeenAt(),
                state.getImportedProject() == null ? null : state.getImportedProject().getId(), state.getNote(), state.getApplication()))
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
            state.getImportedProject() == null ? null : state.getImportedProject().getId(), state.getNote(), state.getApplication());
    }
}
