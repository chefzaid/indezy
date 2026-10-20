package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.JobOffer;
import dev.swirlit.indezy.model.JobOfferState;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.SourceType;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SourceRepository;
import dev.swirlit.indezy.util.FrenchGeography;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Turns a job offer into an opportunity of the workspace's pipeline (status Contact). The publisher
 * becomes the client (a new one is created as an intermediary), and the job board becomes the source:
 * the workspace's source named after the site, created as a job board on first import.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OfferImportService {

    /** Weeks per month, to turn remote days a week into remote days a month. */
    private static final double WEEKS_PER_MONTH = 52.0 / 12;
    private static final int MAX_COLUMN_LENGTH = 255;

    private final OfferStateService offerStates;
    private final JobOfferStateRepository states;
    private final ClientRepository clients;
    private final SourceRepository sources;
    private final FreelanceRepository freelances;
    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final ProjectNoteService projectNotes;

    public ProjectDto importOffer(Long freelanceId, Long offerId) {
        List<JobOfferState> group = offerStates.groupStates(freelanceId, offerId);
        // Its copies on other sites are the same mission: importing one of them imports it.
        for (JobOfferState imported : group) {
            if (imported.getImportedProject() != null) {
                throw new InvalidStateException(String.format(ErrorMessages.OFFER_ALREADY_IMPORTED, imported.getImportedProject().getId()));
            }
        }
        JobOfferState state = group.get(0);
        JobOffer offer = state.getOffer();
        Freelance freelance = state.getFreelance();
        ProjectDto project = new ProjectDto();
        project.setRole(cut(offer.getTitle()));
        project.setStatus(ProjectStatus.CONTACT);
        project.setDescription(offer.getDescription());
        project.setTechStack(offer.getSkills().isEmpty() ? null : cut(String.join(", ", offer.getSkills())));
        project.setDailyRate(dailyRate(offer, freelanceId));
        project.setWorkMode(offer.getWorkMode());
        project.setRemoteDaysPerMonth(remoteDaysPerMonth(offer));
        project.setStartDate(offer.getStartDate());
        project.setDurationInMonths(offer.getDurationMonths());
        project.setLink(offer.getUrl());
        project.setFreelanceId(freelanceId);
        project.setClientId(publisher(freelance, offer).getId());
        project.setSourceId(source(freelance, offer.getSite()).getId());
        ProjectDto created = projectService.create(project);
        state.setImportedProject(projects.getReferenceById(created.getId()));
        if (state.getSeenAt() == null) {
            state.setSeenAt(Instant.now());
        }
        states.save(state);
        // The workspace's note on the mission (on any of its copies) becomes the opportunity's first note.
        group.stream().map(JobOfferState::getNote).filter(Objects::nonNull).findFirst()
            .ifPresent(note -> projectNotes.addNote(created.getId(), note));
        log.info("Imported offer {} as project {}", offerId, created.getId());
        return created;
    }

    /** The highest rate the offer states, otherwise the profile's target, otherwise 0 to fill in. */
    private Integer dailyRate(JobOffer offer, Long freelanceId) {
        if (offer.getRateMax() != null) {
            return offer.getRateMax();
        }
        if (offer.getRateMin() != null) {
            return offer.getRateMin();
        }
        return freelances.findTargetDailyRate(freelanceId).orElse(0);
    }

    private static Integer remoteDaysPerMonth(JobOffer offer) {
        Integer days = offer.getRemoteDaysPerWeek();
        if (days == null || offer.getWorkMode() == WorkMode.ONSITE) {
            return null;
        }
        return (int) Math.round(days * WEEKS_PER_MONTH);
    }

    /** The client named like the publisher (the site itself when the offer names none), created when missing. */
    private Client publisher(Freelance freelance, JobOffer offer) {
        String name = offer.getCompany() != null ? cut(offer.getCompany()) : offer.getSite().getDisplayName();
        return clients.findFirstByFreelanceIdAndCompanyNameIgnoreCase(freelance.getId(), name).orElseGet(() -> {
            Client client = new Client();
            client.setCompanyName(name);
            client.setCity(cityOf(offer));
            client.setIsFinal(false);
            client.setFreelance(freelance);
            return clients.save(client);
        });
    }

    private Source source(Freelance freelance, OfferSite site) {
        return sources.findFirstByFreelanceIdAndNameIgnoreCase(freelance.getId(), site.getDisplayName()).orElseGet(() -> {
            Source source = new Source();
            source.setName(site.getDisplayName());
            source.setType(SourceType.JOB_BOARD);
            source.setLink(site.getHomeUrl());
            source.setIsListing(true);
            source.setFreelance(freelance);
            return sources.save(source);
        });
    }

    /** A client needs a city: the offer's, else its region, else France. */
    private static String cityOf(JobOffer offer) {
        if (offer.getCity() != null) {
            return cut(offer.getCity());
        }
        return FrenchGeography.region(offer.getRegionCode()).map(FrenchGeography.Region::name).orElse("France");
    }

    private static String cut(String text) {
        return text.length() > MAX_COLUMN_LENGTH ? text.substring(0, MAX_COLUMN_LENGTH).strip() : text;
    }
}
