package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.InterviewStepDto;
import dev.swirlit.indezy.dto.OfferApplicationRequest;
import dev.swirlit.indezy.dto.OfferStateDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.JobOfferState;
import dev.swirlit.indezy.model.OfferApplication;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ApplicationChannel;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applying to an offer: the day and channel are kept on every copy of the mission, the mission joins the pipeline
 * (imported at the Contact stage unless it already is) and its opportunity gets an application step waiting for
 * feedback, with a follow-up step planned when asked.
 */
@Slf4j
@Service
@Transactional
public class OfferApplicationService {

    static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    /** When follow-ups and past applications are planned. */
    static final LocalTime MORNING = LocalTime.of(9, 0);

    private static final Map<DocumentLanguage, Map<ApplicationChannel, String>> SENT = Map.of(
        DocumentLanguage.FR, Map.of(ApplicationChannel.SITE_FORM, "Candidature envoyée (formulaire du site)",
            ApplicationChannel.EMAIL, "Candidature envoyée (e-mail)", ApplicationChannel.PHONE, "Candidature faite par téléphone"),
        DocumentLanguage.EN, Map.of(ApplicationChannel.SITE_FORM, "Application sent (site form)",
            ApplicationChannel.EMAIL, "Application sent (email)", ApplicationChannel.PHONE, "Applied by phone"));
    private static final Map<DocumentLanguage, String> FOLLOW_UP = Map.of(
        DocumentLanguage.FR, "Relancer la candidature", DocumentLanguage.EN, "Follow up on the application");

    private final OfferStateService offerStates;
    private final OfferImportService importService;
    private final JobOfferStateRepository states;
    private final InterviewStepService steps;
    private final Clock clock;

    @Autowired
    public OfferApplicationService(OfferStateService offerStates, OfferImportService importService, JobOfferStateRepository states,
                                   InterviewStepService steps) {
        this(offerStates, importService, states, steps, Clock.system(ZONE));
    }

    OfferApplicationService(OfferStateService offerStates, OfferImportService importService, JobOfferStateRepository states,
                            InterviewStepService steps, Clock clock) {
        this.offerStates = offerStates;
        this.importService = importService;
        this.states = states;
        this.steps = steps;
        this.clock = clock;
    }

    public OfferStateDto apply(Long freelanceId, Long offerId, OfferApplicationRequest request) {
        LocalDate today = LocalDate.now(clock.withZone(ZONE));
        if (request.appliedOn().isAfter(today)) {
            throw new IllegalArgumentException(ErrorMessages.OFFER_APPLIED_LATER);
        }
        List<JobOfferState> group = offerStates.groupStates(freelanceId, offerId);
        group.stream().map(JobOfferState::getApplication).filter(Objects::nonNull).findFirst().ifPresent(application -> {
            throw new InvalidStateException(String.format(ErrorMessages.OFFER_ALREADY_APPLIED, application.appliedOn()));
        });
        Long projectId = group.stream().map(JobOfferState::getImportedProject).filter(Objects::nonNull).findFirst()
            .map(Project::getId)
            .orElseGet(() -> importService.importOffer(freelanceId, offerId).getId());
        // Importing saved the offer's state: read the group again so it is not created twice.
        group = offerStates.groupStates(freelanceId, offerId);
        OfferApplication application = new OfferApplication(request.appliedOn(), request.via());
        for (JobOfferState state : group) {
            state.setApplication(application);
            if (state.getSeenAt() == null) {
                state.setSeenAt(clock.instant());
            }
        }
        List<OfferStateDto> saved = states.saveAll(group).stream().map(OfferStateService::toDto).toList();
        DocumentLanguage language = request.language() == null ? DocumentLanguage.EN : request.language();
        JobOffer offer = group.get(0).getOffer();
        LocalDateTime sentAt = request.appliedOn().equals(today) ?
            LocalDateTime.now(clock.withZone(ZONE)).truncatedTo(ChronoUnit.MINUTES) : request.appliedOn().atTime(MORNING);
        addStep(projectId, SENT.get(language).get(request.via()), sentAt, StepStatus.WAITING_FEEDBACK, offer);
        if (request.followUpInDays() != null) {
            LocalDate followUp = request.appliedOn().plusDays(request.followUpInDays());
            addStep(projectId, FOLLOW_UP.get(language), (followUp.isAfter(today) ? followUp : today.plusDays(1)).atTime(MORNING),
                StepStatus.PLANNED, offer);
        }
        log.info("Workspace {} applied to offer {} (project {})", freelanceId, offerId, projectId);
        return OfferStateDto.merged(offerId, saved);
    }

    /** Forgets the application; the opportunity and its steps stay in the pipeline. */
    public OfferStateDto withdraw(Long freelanceId, Long offerId) {
        List<JobOfferState> group = offerStates.groupStates(freelanceId, offerId);
        group.forEach(state -> state.setApplication(null));
        return OfferStateDto.merged(offerId, states.saveAll(group).stream().map(OfferStateService::toDto).toList());
    }

    private void addStep(Long projectId, String title, LocalDateTime date, StepStatus status, JobOffer offer) {
        InterviewStepDto step = new InterviewStepDto();
        step.setProjectId(projectId);
        step.setTitle(title);
        step.setDate(date);
        step.setStatus(status);
        step.setNotes(offer.getSite().getDisplayName() + " · " + offer.getUrl());
        steps.create(step);
    }
}
