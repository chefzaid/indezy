package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.OfferDraftDto;
import dev.swirlit.indezy.dto.PostingImportRequest;
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
import java.util.Optional;

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
    /** A client needs a city: a posting's publisher is placed in France until the workspace knows better. */
    private static final String POSTING_CLIENT_CITY = "France";

    private final OfferStateService offerStates;
    private final JobOfferStateRepository states;
    private final ClientRepository clients;
    private final SourceRepository sources;
    private final FreelanceRepository freelances;
    private final ProjectRepository projects;
    private final ProjectService projectService;
    private final ProjectNoteService projectNotes;

    public ProjectDto importOffer(Long freelanceId, Long offerId) {
        return importOffer(freelanceId, offerId, null);
    }

    /**
     * Adds the mission to the pipeline as {@link #draft} fills it in or, when given, as {@code edits} (that draft
     * corrected in the project form) says: an unset client is the publisher and an unset source the site, each
     * created when the workspace has none of that name, and an unset stage is Contact.
     */
    public ProjectDto importOffer(Long freelanceId, Long offerId, ProjectDto edits) {
        List<JobOfferState> group = offerStates.groupStates(freelanceId, offerId);
        // Its copies on other sites are the same mission: importing one of them imports it.
        OfferStateService.pipelineProject(group).ifPresent(projectId -> {
            throw new InvalidStateException(String.format(ErrorMessages.OFFER_ALREADY_IMPORTED, projectId));
        });
        JobOfferState state = group.get(0);
        JobOffer offer = state.getOffer();
        Freelance freelance = state.getFreelance();
        ProjectDto project = edits != null ? edits : prefill(offer, freelanceId);
        project.setId(null);
        project.setFreelanceId(freelanceId);
        if (project.getStatus() == null) {
            project.setStatus(ProjectStatus.CONTACT);
        }
        if (project.getLink() == null) {
            project.setLink(offer.getUrl());
        }
        if (project.getClientId() == null) {
            project.setClientId(publisher(freelance, offer).getId());
        }
        if (project.getSourceId() == null) {
            project.setSourceId(source(freelance, offer.getSite()).getId());
        }
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

    /**
     * The opportunity adding the offer would make, nothing created: its client and source are set when the workspace
     * already has the publisher and the site, and named in any case.
     */
    @Transactional(readOnly = true)
    public OfferDraftDto draft(Long freelanceId, Long offerId) {
        JobOffer offer = offerStates.groupStates(freelanceId, offerId).get(0).getOffer();
        ProjectDto project = prefill(offer, freelanceId);
        String clientName = publisherName(offer);
        String sourceName = offer.getSite().getDisplayName();
        clients.findFirstByFreelanceIdAndCompanyNameIgnoreCase(freelanceId, clientName).ifPresent(client -> project.setClientId(client.getId()));
        sources.findFirstByFreelanceIdAndNameIgnoreCase(freelanceId, sourceName).ifPresent(source -> project.setSourceId(source.getId()));
        return new OfferDraftDto(project, clientName, sourceName, null);
    }

    /**
     * The opportunity a posting read from a site the feed does not collect would make, nothing created: like
     * {@link #draft}, its client named after the posting's publisher (else the site) and its source after the site.
     */
    @Transactional(readOnly = true)
    public OfferDraftDto draftPosting(Long freelanceId, JobOffer posting, PostingSite site) {
        ProjectDto project = prefill(posting, freelanceId);
        String clientName = posting.getCompany() != null ? cut(posting.getCompany()) : site.name();
        clients.findFirstByFreelanceIdAndCompanyNameIgnoreCase(freelanceId, clientName).ifPresent(client -> project.setClientId(client.getId()));
        sources.findFirstByFreelanceIdAndNameIgnoreCase(freelanceId, site.name()).ifPresent(source -> project.setSourceId(source.getId()));
        return new OfferDraftDto(project, clientName, site.name(), site.homeUrl());
    }

    /** The workspace's opportunity made from the posting at {@code link}, if any. */
    @Transactional(readOnly = true)
    public Optional<Long> postingProject(Long freelanceId, String link) {
        return projects.findIdsByFreelanceIdAndLink(freelanceId, link).stream().findFirst();
    }

    /**
     * Adds a posting read from a site the feed does not collect to the pipeline, as the project form corrected its draft:
     * an unset client or source is the one the request names, created when missing, and an unset stage is Contact. A
     * posting whose link is in the pipeline already is refused, once per mission as for the feed's offers.
     */
    public ProjectDto importPosting(Long freelanceId, PostingImportRequest request) {
        ProjectDto project = request.getProject();
        if (project.getLink() != null) {
            postingProject(freelanceId, project.getLink()).ifPresent(projectId -> {
                throw new InvalidStateException(String.format(ErrorMessages.OFFER_ALREADY_IMPORTED, projectId));
            });
        }
        Freelance freelance = freelances.getReferenceById(freelanceId);
        project.setId(null);
        project.setFreelanceId(freelanceId);
        if (project.getStatus() == null) {
            project.setStatus(ProjectStatus.CONTACT);
        }
        if (project.getClientId() == null) {
            project.setClientId(client(freelance, cut(request.getClientName()), POSTING_CLIENT_CITY).getId());
        }
        if (project.getSourceId() == null) {
            project.setSourceId(source(freelance, cut(request.getSourceName()), request.getSourceLink()).getId());
        }
        ProjectDto created = projectService.create(project);
        log.info("Imported a posting from another site as project {}", created.getId());
        return created;
    }

    private ProjectDto prefill(JobOffer offer, Long freelanceId) {
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
        project.setWorkAddress(workAddress(offer));
        project.setFreelanceId(freelanceId);
        return project;
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
        return client(freelance, publisherName(offer), cityOf(offer));
    }

    /** The workspace's client of that name, else a new intermediary in {@code city}. */
    private Client client(Freelance freelance, String name, String city) {
        return clients.findFirstByFreelanceIdAndCompanyNameIgnoreCase(freelance.getId(), name).orElseGet(() -> {
            Client client = new Client();
            client.setCompanyName(name);
            client.setCity(city);
            client.setIsFinal(false);
            client.setFreelance(freelance);
            return clients.save(client);
        });
    }

    private static String publisherName(JobOffer offer) {
        return offer.getCompany() != null ? cut(offer.getCompany()) : offer.getSite().getDisplayName();
    }

    private Source source(Freelance freelance, OfferSite site) {
        return source(freelance, site.getDisplayName(), site.getHomeUrl());
    }

    /** The workspace's source of that name, else a new job board at {@code link}. */
    private Source source(Freelance freelance, String name, String link) {
        return sources.findFirstByFreelanceIdAndNameIgnoreCase(freelance.getId(), name).orElseGet(() -> {
            Source source = new Source();
            source.setName(name);
            source.setType(SourceType.JOB_BOARD);
            source.setLink(link);
            source.setIsListing(true);
            source.setFreelance(freelance);
            return sources.save(source);
        });
    }

    /** The offer's place as an address its commute can be looked up from ("92400 Courbevoie"); none without a city. */
    static String workAddress(JobOffer offer) {
        if (offer.getCity() == null || offer.getCity().isBlank()) {
            return null;
        }
        return cut(offer.getPostalCode() == null ? offer.getCity().strip() : offer.getPostalCode().strip() + " " + offer.getCity().strip());
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
