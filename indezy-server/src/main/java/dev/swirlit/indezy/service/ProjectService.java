package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.constants.UserPreferenceDefaults;
import dev.swirlit.indezy.dto.KanbanBoardDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.ProjectMapper;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.RefusedBy;
import dev.swirlit.indezy.repository.ActivityReportRepository;
import dev.swirlit.indezy.repository.PurchaseOrderRepository;
import dev.swirlit.indezy.model.enums.WebhookEvent;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import dev.swirlit.indezy.repository.ProjectAttachmentRepository;
import dev.swirlit.indezy.repository.ProjectNoteRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SourceRepository;
import dev.swirlit.indezy.util.SkillNames;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final FreelanceRepository freelanceRepository;
    private final ClientRepository clientRepository;
    private final SourceRepository sourceRepository;
    private final InterviewStepRepository interviewStepRepository;
    private final ProjectNoteRepository projectNoteRepository;
    private final ProjectAttachmentRepository projectAttachmentRepository;
    private final ContactInteractionRepository contactInteractionRepository;
    private final ActivityReportRepository activityReportRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final JobOfferStateRepository jobOfferStateRepository;
    private final ProjectMapper projectMapper;
    private final SeasonService seasonService;

    private WebhookPublisher webhooks = WebhookPublisher.NONE;

    /** Tells the workspace's webhooks of the changes; none until Spring sets it (unit tests build the service by hand). */
    @Autowired
    void setWebhooks(WebhookPublisher webhooks) {
        this.webhooks = webhooks;
    }

    private static final List<ProjectStatus> KANBAN_COLUMN_ORDER = Arrays.asList(
        ProjectStatus.CONTACT,
        ProjectStatus.INTERVIEW,
        ProjectStatus.OFFER,
        ProjectStatus.LOST,
        ProjectStatus.WON
    );

    @Transactional(readOnly = true)
    public ProjectDto findById(Long id) {
        log.debug("Finding project by id: {}", id);
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, id)));
        return projectMapper.toDto(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> findByFreelanceId(Long freelanceId) {
        log.debug("Finding projects by freelance id: {}", freelanceId);
        return projectRepository.findByFreelanceId(freelanceId)
            .stream()
            .map(projectMapper::toDto)
            .toList();
    }

    /**
     * Renames a skill/tech-stack tag across every project of a freelance. Matching is
     * case-insensitive; duplicates produced by the rename are collapsed. Returns the number
     * of projects that were changed.
     */
    @org.springframework.transaction.annotation.Transactional
    public int renameTag(Long freelanceId, String from, String to) {
        String fromTag = from == null ? "" : from.trim();
        String toTag = to == null ? "" : to.trim();
        if (fromTag.isEmpty() || toTag.isEmpty()) {
            throw new IllegalArgumentException("Both the current and new tag names are required");
        }

        int updated = 0;
        for (Project project : projectRepository.findByFreelanceId(freelanceId)) {
            String renamed = renameTechStack(project.getTechStack(), fromTag, toTag);
            if (renamed != null) {
                project.setTechStack(renamed);
                projectRepository.save(project);
                updated++;
            }
        }
        return updated;
    }

    private String renameTechStack(String techStack, String fromTag, String toTag) {
        if (techStack == null || techStack.isBlank()) {
            return null;
        }
        List<String> renamed = new ArrayList<>();
        boolean changed = false;
        for (String rawTag : techStack.split(",")) {
            String tag = rawTag.trim();
            if (tag.isEmpty()) {
                continue;
            }
            String next = tag.equalsIgnoreCase(fromTag) ? toTag : tag;
            changed |= !next.equals(tag);
            // Renaming "JS" to "JavaScript" also folds a "JavaScript" already there.
            if (renamed.stream().noneMatch(existing -> SkillNames.same(existing, next))) {
                renamed.add(next);
            } else {
                changed = true;
            }
        }
        return changed ? String.join(", ", renamed) : null;
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> findByClientId(Long clientId) {
        log.debug("Finding projects by client id: {}", clientId);
        return projectRepository.findByClientId(clientId)
            .stream()
            .map(projectMapper::toDto)
            .toList();
    }

    public ProjectDto create(ProjectDto projectDto) {
        log.debug("Creating new project: {}", projectDto.getRole());
        
        if (projectDto.getClientId() == null) {
            throw new IllegalArgumentException(ErrorMessages.PROJECT_CLIENT_REQUIRED);
        }

        Project project = projectMapper.toEntity(projectDto);
        
        // Default status if not provided
        if (project.getStatus() == null) {
            project.setStatus(ProjectStatus.CONTACT);
        }
        // Who refused and why only apply to refused opportunities, the end of a mission to won ones.
        project.clearOtherOutcomes();
        
        // Set required relationships
        if (projectDto.getFreelanceId() != null) {
            Freelance freelance = freelanceRepository.findById(projectDto.getFreelanceId())
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.FREELANCE_NOT_FOUND, projectDto.getFreelanceId())));
            project.setFreelance(freelance);
        }
        
        if (projectDto.getClientId() != null) {
            Client client = clientRepository.findById(projectDto.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.CLIENT_NOT_FOUND, projectDto.getClientId())));
            project.setClient(client);
        }
        
        if (projectDto.getMiddlemanId() != null) {
            Client middleman = clientRepository.findById(projectDto.getMiddlemanId())
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.CLIENT_NOT_FOUND, projectDto.getMiddlemanId())));
            project.setMiddleman(middleman);
        }
        
        if (projectDto.getSourceId() != null) {
            Source source = sourceRepository.findById(projectDto.getSourceId())
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.SOURCE_NOT_FOUND, projectDto.getSourceId())));
            project.setSource(source);
        }

        // Explicit season, otherwise the season running today (if any).
        Long freelanceId = project.getFreelance() != null ? project.getFreelance().getId() : null;
        if (projectDto.getSeasonId() != null) {
            project.setSeason(seasonService.getSeasonOfFreelance(projectDto.getSeasonId(), freelanceId));
        } else if (freelanceId != null) {
            seasonService.findCurrentSeason(freelanceId).ifPresent(project::setSeason);
        }
        
        Project savedProject = projectRepository.save(project);
        webhooks.publish(freelanceId, WebhookEvent.OPPORTUNITY_CREATED, WebhookPayloads.project(savedProject, null));
        
        log.info("Created project with id: {}", savedProject.getId());
        return projectMapper.toDto(savedProject);
    }

    public ProjectDto update(Long id, ProjectDto projectDto) {
        log.debug("Updating project with id: {}", id);
        
        Project existingProject = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, id)));

        ProjectStatus previousStatus = existingProject.getStatus();
        projectMapper.updateEntity(projectDto, existingProject);

        // A payload without a status (older clients, partial forms) must not wipe the pipeline stage.
        if (existingProject.getStatus() == null) {
            existingProject.setStatus(previousStatus != null ? previousStatus : ProjectStatus.CONTACT);
        }
        existingProject.clearOtherOutcomes();
        
        // Update relationships if provided
        if (projectDto.getClientId() != null && !projectDto.getClientId().equals(existingProject.getClient().getId())) {
            Client client = clientRepository.findById(projectDto.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.CLIENT_NOT_FOUND, projectDto.getClientId())));
            existingProject.setClient(client);
        }
        
        if (projectDto.getMiddlemanId() != null) {
            if (existingProject.getMiddleman() == null || !projectDto.getMiddlemanId().equals(existingProject.getMiddleman().getId())) {
                Client middleman = clientRepository.findById(projectDto.getMiddlemanId())
                    .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.CLIENT_NOT_FOUND, projectDto.getMiddlemanId())));
                existingProject.setMiddleman(middleman);
            }
        } else {
            existingProject.setMiddleman(null);
        }
        
        if (projectDto.getSourceId() != null) {
            if (existingProject.getSource() == null || !projectDto.getSourceId().equals(existingProject.getSource().getId())) {
                Source source = sourceRepository.findById(projectDto.getSourceId())
                    .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.SOURCE_NOT_FOUND, projectDto.getSourceId())));
                existingProject.setSource(source);
            }
        } else {
            existingProject.setSource(null);
        }

        if (projectDto.getSeasonId() != null) {
            if (existingProject.getSeason() == null || !projectDto.getSeasonId().equals(existingProject.getSeason().getId())) {
                Long ownerId = existingProject.getFreelance() != null ? existingProject.getFreelance().getId() : null;
                existingProject.setSeason(seasonService.getSeasonOfFreelance(projectDto.getSeasonId(), ownerId));
            }
        } else {
            existingProject.setSeason(null);
        }
        
        Project updatedProject = projectRepository.save(existingProject);
        publishStatusChange(updatedProject, previousStatus);
        
        log.info("Updated project with id: {}", updatedProject.getId());
        return projectMapper.toDto(updatedProject);
    }

    public void delete(Long id) {
        log.debug("Deleting project with id: {}", id);
        
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, id));
        }

        // Interview steps cascade with the project; journal notes, files and activity reports are not mapped on it.
        contactInteractionRepository.clearProject(id);
        projectNoteRepository.deleteByProjectId(id);
        projectAttachmentRepository.deleteByProjectId(id);
        activityReportRepository.deleteByProjectId(id);
        purchaseOrderRepository.deleteByProjectId(id);
        // An offer imported as this project can be imported again.
        jobOfferStateRepository.clearImportedProject(id);
        projectRepository.deleteById(id);
        log.info("Deleted project with id: {}", id);
    }

    public ProjectDto updateStatus(Long id, ProjectStatus status, RefusedBy refusedBy, LostReason lostReason) {
        log.debug("Updating project status with id: {} to status: {}", id, status);
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, id)));
        ProjectStatus previousStatus = project.getStatus();
        project.setStatus(status);
        project.setRefusedBy(refusedBy);
        project.setLostReason(lostReason);
        // Who refused and why only apply to refused opportunities, the end of a mission to won ones.
        project.clearOtherOutcomes();
        Project updatedProject = projectRepository.save(project);
        publishStatusChange(updatedProject, previousStatus);
        log.info("Updated project status with id: {} to status: {}", id, status);
        return projectMapper.toDto(updatedProject);
    }

    /** Tells the webhooks an opportunity changed stage, when it did. */
    void publishStatusChange(Project project, ProjectStatus previousStatus) {
        if (project.getStatus() != previousStatus && project.getFreelance() != null) {
            webhooks.publish(project.getFreelance().getId(), WebhookEvent.OPPORTUNITY_STATUS_CHANGED, WebhookPayloads.project(project, previousStatus));
        }
    }

    /** Kanban board of a workspace, limited to one season's opportunities when a season is given. */
    @Transactional(readOnly = true)
    public KanbanBoardDto getKanbanBoard(Long freelanceId, Long seasonId) {
        log.debug("Getting kanban board for freelance: {} (season: {})", freelanceId, seasonId);

        List<Project> projects = (seasonId != null ?
            projectRepository.findByFreelanceIdAndSeasonId(freelanceId, seasonId) :
            projectRepository.findByFreelanceId(freelanceId)).stream()
            .filter(project -> project.getArchivedAt() == null)
            .toList();

        Map<String, List<KanbanBoardDto.ProjectCardDto>> columns = new LinkedHashMap<>();
        for (ProjectStatus status : KANBAN_COLUMN_ORDER) {
            columns.put(status.name(), new ArrayList<>());
        }

        Set<Long> duplicateIds = findPotentialDuplicateIds(projects);

        for (Project project : projects) {
            ProjectStatus status = project.getStatus() != null ? project.getStatus() : ProjectStatus.CONTACT;
            KanbanBoardDto.ProjectCardDto card = createProjectCard(project);
            card.setIsPotentialDuplicate(duplicateIds.contains(project.getId()));
            String statusKey = status.name();
            if (columns.containsKey(statusKey)) {
                columns.get(statusKey).add(card);
            }
        }

        // Pin favorites to the top, then honour the manual board position (cards without one last).
        Comparator<KanbanBoardDto.ProjectCardDto> byFavorite =
            Comparator.comparingInt(card -> Boolean.TRUE.equals(card.getIsFavorite()) ? 0 : 1);
        Comparator<KanbanBoardDto.ProjectCardDto> byPosition =
            Comparator.comparing(KanbanBoardDto.ProjectCardDto::getBoardPosition,
                Comparator.nullsLast(Comparator.naturalOrder()));
        columns.values().forEach(cards -> cards.sort(byFavorite.thenComparing(byPosition)));

        KanbanBoardDto kanbanBoard = new KanbanBoardDto();
        kanbanBoard.setInactivityDays(freelanceRepository.findInactivityDays(freelanceId)
            .orElse(UserPreferenceDefaults.INACTIVITY_DAYS));
        kanbanBoard.setColumns(columns);
        kanbanBoard.setColumnOrder(KANBAN_COLUMN_ORDER.stream().map(Enum::name).toList());
        return kanbanBoard;
    }

    /**
     * Ends a won mission on the day given, saying why: its activity reports stop with that month, and it leaves the
     * missions in progress once that month is over. It stays won; {@link #resumeMission} undoes it.
     */
    public ProjectDto endMission(Long id, LocalDate endedOn, String reason) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, id)));
        if (!ProjectStatus.WON.equals(project.getStatus())) {
            throw new InvalidStateException("Only an accepted mission can be ended");
        }
        if (project.getStartDate() != null && endedOn.isBefore(project.getStartDate())) {
            throw new IllegalArgumentException("A mission cannot end before it starts");
        }
        project.setEndedOn(endedOn);
        project.setEndReason(reason.strip());
        Project saved = projectRepository.save(project);
        log.info("Ended mission with id: {} on {}", id, endedOn);
        return projectMapper.toDto(saved);
    }

    /** Puts a mission ended by mistake back in progress. */
    public ProjectDto resumeMission(Long id) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, id)));
        if (project.getEndedOn() != null) {
            project.setEndedOn(null);
            project.setEndReason(null);
            project = projectRepository.save(project);
            log.info("Resumed mission with id: {}", id);
        }
        return projectMapper.toDto(project);
    }

    /**
     * Takes an opportunity out of the pipeline into the archive, or restores it to its Kanban column: its status is
     * kept either way, and restoring it counts as activity, so the inactivity period starts again.
     */
    public ProjectDto setArchived(Long id, boolean archived) {
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, id)));
        if (archived != (project.getArchivedAt() != null)) {
            project.setArchivedAt(archived ? LocalDateTime.now() : null);
            project = projectRepository.save(project);
            log.info("{} project with id: {}", archived ? "Archived" : "Restored", id);
        }
        return projectMapper.toDto(project);
    }

    public ProjectDto toggleFavorite(Long id) {
        log.debug("Toggling favorite flag for project with id: {}", id);
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, id)));
        project.setIsFavorite(!Boolean.TRUE.equals(project.getIsFavorite()));
        Project updatedProject = projectRepository.save(project);
        log.info("Toggled favorite flag for project with id: {} to: {}", id, updatedProject.getIsFavorite());
        return projectMapper.toDto(updatedProject);
    }

    /**
     * Persists the manual order of cards within a Kanban column: each project is assigned a
     * board position matching its index in {@code orderedProjectIds}. Ids that do not belong to
     * the given freelance are ignored, so a client cannot reorder another freelance's cards.
     */
    public void reorderKanbanColumn(Long freelanceId, List<Long> orderedProjectIds) {
        log.debug("Reordering {} kanban cards for freelance: {}", orderedProjectIds.size(), freelanceId);
        Map<Long, Project> projectsById = new HashMap<>();
        for (Project project : projectRepository.findAllById(orderedProjectIds)) {
            projectsById.put(project.getId(), project);
        }

        List<Project> toSave = new ArrayList<>();
        for (int position = 0; position < orderedProjectIds.size(); position++) {
            Project project = projectsById.get(orderedProjectIds.get(position));
            if (project != null && project.getFreelance() != null &&
                project.getFreelance().getId().equals(freelanceId)) {
                project.setBoardPosition(position);
                toSave.add(project);
            }
        }
        projectRepository.saveAll(toSave);
        log.info("Reordered {} kanban cards for freelance: {}", toSave.size(), freelanceId);
    }

    private KanbanBoardDto.ProjectCardDto createProjectCard(Project project) {
        KanbanBoardDto.ProjectCardDto card = new KanbanBoardDto.ProjectCardDto();
        card.setProjectId(project.getId());
        card.setRole(project.getRole());
        card.setStatus(project.getStatus() != null ? project.getStatus().name() : ProjectStatus.CONTACT.name());
        card.setClientName(project.getClient() != null ? project.getClient().getCompanyName() : null);
        card.setDailyRate(project.getDailyRate());
        card.setWorkMode(project.getWorkMode() != null ? project.getWorkMode().toString() : null);
        card.setTechStack(project.getTechStack());
        card.setSourceName(project.getSource() != null ? project.getSource().getName() : null);
        card.setStartDate(project.getStartDate() != null ? project.getStartDate().toString() : null);
        card.setDurationInMonths(project.getDurationInMonths());
        card.setNotes(project.getNotes());
        card.setPersonalRating(project.getPersonalRating());
        card.setIsFavorite(project.getIsFavorite());
        card.setBoardPosition(project.getBoardPosition());
        card.setUpdatedAt(project.getUpdatedAt() != null ? project.getUpdatedAt().toString() : null);
        card.setRefusedBy(project.getRefusedBy() != null ? project.getRefusedBy().name() : null);
        card.setLostReason(project.getLostReason() != null ? project.getLostReason().name() : null);

        List<InterviewStep> steps = interviewStepRepository.findByProjectId(project.getId());
        card.setTotalSteps(steps.size());
        card.setCompletedSteps((int) steps.stream().filter(InterviewStep::isCompleted).count());
        card.setFailedSteps((int) steps.stream().filter(InterviewStep::isFailed).count());

        return card;
    }

    /**
     * Flags opportunities that look like duplicates: the same client and the same role
     * (case-insensitive), typically the same posting captured from several job boards.
     * Returns the ids of every project that shares its client+role with at least one other.
     */
    private Set<Long> findPotentialDuplicateIds(List<Project> projects) {
        Map<String, List<Long>> byClientAndRole = new HashMap<>();
        for (Project project : projects) {
            if (project.getClient() == null || project.getRole() == null) {
                continue;
            }
            String key = project.getClient().getId() + "|" + project.getRole().trim().toLowerCase(Locale.ROOT);
            byClientAndRole.computeIfAbsent(key, k -> new ArrayList<>()).add(project.getId());
        }

        Set<Long> duplicateIds = new HashSet<>();
        for (List<Long> ids : byClientAndRole.values()) {
            if (ids.size() > 1) {
                duplicateIds.addAll(ids);
            }
        }
        return duplicateIds;
    }
}
