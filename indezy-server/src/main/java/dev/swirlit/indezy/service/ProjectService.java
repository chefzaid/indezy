package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.dto.KanbanBoardDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.ProjectMapper;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import java.util.TreeMap;

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
    private final ProjectMapper projectMapper;

    private static final List<ProjectStatus> KANBAN_COLUMN_ORDER = Arrays.asList(
        ProjectStatus.IDENTIFIED,
        ProjectStatus.APPLIED,
        ProjectStatus.INTERVIEW,
        ProjectStatus.OFFER,
        ProjectStatus.WON,
        ProjectStatus.LOST
    );

    @Transactional(readOnly = true)
    public List<ProjectDto> findAll() {
        log.debug("Finding all projects");
        return projectRepository.findAll()
            .stream()
            .map(projectMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDto findById(Long id) {
        log.debug("Finding project by id: {}", id);
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, id)));
        return projectMapper.toDto(project);
    }

    @Transactional(readOnly = true)
    public ProjectDto findByIdWithSteps(Long id) {
        log.debug("Finding project with steps by id: {}", id);
        Project project = projectRepository.findByIdWithSteps(id)
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

    @Transactional(readOnly = true)
    public List<ProjectDto> findByClientId(Long clientId) {
        log.debug("Finding projects by client id: {}", clientId);
        return projectRepository.findByClientId(clientId)
            .stream()
            .map(projectMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> findByFreelanceIdAndFilters(Long freelanceId, Integer minRate, Integer maxRate, 
                                                       WorkMode workMode, LocalDate startDateAfter, String techStack) {
        log.debug("Finding projects by freelance id with filters: {}", freelanceId);
        
        List<Project> projects = projectRepository.findByFreelanceId(freelanceId);
        
        // Apply filters
        if (minRate != null) {
            projects = projects.stream()
                .filter(p -> p.getDailyRate() != null && p.getDailyRate() >= minRate)
                .toList();
        }
        
        if (maxRate != null) {
            projects = projects.stream()
                .filter(p -> p.getDailyRate() != null && p.getDailyRate() <= maxRate)
                .toList();
        }
        
        if (workMode != null) {
            projects = projects.stream()
                .filter(p -> workMode.equals(p.getWorkMode()))
                .toList();
        }
        
        if (startDateAfter != null) {
            projects = projects.stream()
                .filter(p -> p.getStartDate() != null && p.getStartDate().isAfter(startDateAfter))
                .toList();
        }
        
        if (techStack != null && !techStack.trim().isEmpty()) {
            projects = projects.stream()
                .filter(p -> p.getTechStack() != null &&
                           p.getTechStack().toLowerCase(Locale.ROOT).contains(techStack.toLowerCase(Locale.ROOT)))
                .toList();
        }
        
        return projects.stream()
            .map(projectMapper::toDto)
            .toList();
    }

    public ProjectDto create(ProjectDto projectDto) {
        log.debug("Creating new project: {}", projectDto.getRole());
        
        Project project = projectMapper.toEntity(projectDto);
        
        // Default status if not provided
        if (project.getStatus() == null) {
            project.setStatus(ProjectStatus.IDENTIFIED);
        }
        
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
        
        Project savedProject = projectRepository.save(project);
        
        log.info("Created project with id: {}", savedProject.getId());
        return projectMapper.toDto(savedProject);
    }

    public ProjectDto update(Long id, ProjectDto projectDto) {
        log.debug("Updating project with id: {}", id);
        
        Project existingProject = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, id)));

        projectMapper.updateEntity(projectDto, existingProject);
        
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
        
        Project updatedProject = projectRepository.save(existingProject);
        
        log.info("Updated project with id: {}", updatedProject.getId());
        return projectMapper.toDto(updatedProject);
    }

    public void delete(Long id) {
        log.debug("Deleting project with id: {}", id);
        
        if (!projectRepository.existsById(id)) {
            throw new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, id));
        }

        projectRepository.deleteById(id);
        log.info("Deleted project with id: {}", id);
    }

    @Transactional(readOnly = true)
    public Double getAverageDailyRateByFreelanceId(Long freelanceId) {
        return projectRepository.findAverageDailyRateByFreelanceId(freelanceId);
    }

    @Transactional(readOnly = true)
    public Long countByFreelanceId(Long freelanceId) {
        return projectRepository.countByFreelanceId(freelanceId);
    }

    public ProjectDto updateStatus(Long id, ProjectStatus status, LostReason lostReason) {
        log.debug("Updating project status with id: {} to status: {}", id, status);
        Project project = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.PROJECT_NOT_FOUND, id)));
        project.setStatus(status);
        // The lost reason only applies to lost opportunities; clear it otherwise.
        project.setLostReason(ProjectStatus.LOST.equals(status) ? lostReason : null);
        Project updatedProject = projectRepository.save(project);
        log.info("Updated project status with id: {} to status: {}", id, status);
        return projectMapper.toDto(updatedProject);
    }

    @Transactional(readOnly = true)
    public KanbanBoardDto getKanbanBoard(Long freelanceId) {
        log.debug("Getting kanban board for freelance: {}", freelanceId);

        List<Project> projects = projectRepository.findByFreelanceId(freelanceId);

        Map<String, List<KanbanBoardDto.ProjectCardDto>> columns = new LinkedHashMap<>();
        for (ProjectStatus status : KANBAN_COLUMN_ORDER) {
            columns.put(status.name(), new ArrayList<>());
        }

        Set<Long> duplicateIds = findPotentialDuplicateIds(projects);

        for (Project project : projects) {
            ProjectStatus status = project.getStatus() != null ? project.getStatus() : ProjectStatus.IDENTIFIED;
            KanbanBoardDto.ProjectCardDto card = createProjectCard(project);
            card.setIsPotentialDuplicate(duplicateIds.contains(project.getId()));
            String statusKey = status.name();
            if (columns.containsKey(statusKey)) {
                columns.get(statusKey).add(card);
            }
        }

        // Pin favorites to the top of each column while preserving the existing order otherwise.
        columns.values().forEach(cards ->
            cards.sort(Comparator.comparing(
                card -> Boolean.TRUE.equals(card.getIsFavorite()) ? 0 : 1)));

        KanbanBoardDto kanbanBoard = new KanbanBoardDto();
        kanbanBoard.setColumns(columns);
        kanbanBoard.setColumnOrder(KANBAN_COLUMN_ORDER.stream().map(Enum::name).toList());
        return kanbanBoard;
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

    private KanbanBoardDto.ProjectCardDto createProjectCard(Project project) {
        KanbanBoardDto.ProjectCardDto card = new KanbanBoardDto.ProjectCardDto();
        card.setProjectId(project.getId());
        card.setRole(project.getRole());
        card.setStatus(project.getStatus() != null ? project.getStatus().name() : ProjectStatus.IDENTIFIED.name());
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
        card.setUpdatedAt(project.getUpdatedAt() != null ? project.getUpdatedAt().toString() : null);
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

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats(Long freelanceId) {
        log.debug("Getting dashboard stats for freelance: {}", freelanceId);

        Long totalProjects = projectRepository.countByFreelanceId(freelanceId);
        Double averageDailyRate = projectRepository.findAverageDailyRateByFreelanceId(freelanceId);
        Long wonProjects = projectRepository.countWonByFreelanceId(freelanceId);
        Long lostProjects = projectRepository.countLostByFreelanceId(freelanceId);
        Long activeProjects = projectRepository.countActiveByFreelanceId(freelanceId);

        // Projects by status
        Map<String, Long> projectsByStatus = new LinkedHashMap<>();
        for (ProjectStatus status : ProjectStatus.values()) {
            projectsByStatus.put(status.name(), 0L);
        }
        for (Object[] row : projectRepository.countByFreelanceIdGroupByStatus(freelanceId)) {
            ProjectStatus status = (ProjectStatus) row[0];
            Long count = (Long) row[1];
            projectsByStatus.put(status.name(), count);
        }

        // Projects by work mode
        Map<String, Long> projectsByWorkMode = new LinkedHashMap<>();
        for (WorkMode mode : WorkMode.values()) {
            projectsByWorkMode.put(mode.name(), 0L);
        }
        for (Object[] row : projectRepository.countByFreelanceIdGroupByWorkMode(freelanceId)) {
            WorkMode mode = (WorkMode) row[0];
            Long count = (Long) row[1];
            projectsByWorkMode.put(mode.name(), count);
        }

        // Daily rate ranges
        List<Project> projects = projectRepository.findByFreelanceId(freelanceId);

        // Lost-reason breakdown (only lost opportunities that carry a reason)
        Map<String, Long> lostReasonsBreakdown = new LinkedHashMap<>();
        for (LostReason reason : LostReason.values()) {
            lostReasonsBreakdown.put(reason.name(), 0L);
        }
        projects.stream()
            .filter(p -> ProjectStatus.LOST.equals(p.getStatus()) && p.getLostReason() != null)
            .forEach(p -> lostReasonsBreakdown.merge(p.getLostReason().name(), 1L, Long::sum));
        int[][] ranges = {{0, 300}, {300, 500}, {500, 700}, {700, 900}, {900, Integer.MAX_VALUE}};
        String[] rangeLabels = {"0-300", "300-500", "500-700", "700-900", "900+"};
        List<DashboardStatsDto.DailyRateRange> dailyRateRanges = new ArrayList<>();
        for (int i = 0; i < ranges.length; i++) {
            int min = ranges[i][0];
            int max = ranges[i][1];
            long count = projects.stream()
                .filter(p -> p.getDailyRate() != null && p.getDailyRate() >= min && p.getDailyRate() < max)
                .count();
            dailyRateRanges.add(DashboardStatsDto.DailyRateRange.builder()
                .label(rangeLabels[i])
                .count(count)
                .build());
        }

        // Total estimated revenue
        double totalRevenue = projects.stream()
            .filter(p -> p.getTotalRevenue() != null)
            .mapToDouble(Project::getTotalRevenue)
            .sum();

        // Forecast revenue: total revenue weighted by each opportunity's win probability
        double forecastRevenue = projects.stream()
            .filter(p -> p.getForecastRevenue() != null)
            .mapToDouble(Project::getForecastRevenue)
            .sum();

        List<DashboardStatsDto.SourceRoi> sourceRoi = buildSourceRoiRanking(projects);
        List<DashboardStatsDto.DailyRateEvolution> dailyRateEvolution = buildDailyRateEvolution(projects);

        // Bench time: idle days between consecutive signed missions and their estimated cost
        double resolvedAverageRate = averageDailyRate != null ? averageDailyRate : 0;
        long[] bench = buildBenchStats(projects);

        return DashboardStatsDto.builder()
            .totalProjects(totalProjects != null ? totalProjects : 0)
            .averageDailyRate(averageDailyRate != null ? averageDailyRate : 0)
            .totalEstimatedRevenue(totalRevenue)
            .forecastRevenue(forecastRevenue)
            .activeProjects(activeProjects != null ? activeProjects : 0)
            .wonProjects(wonProjects != null ? wonProjects : 0)
            .lostProjects(lostProjects != null ? lostProjects : 0)
            .totalBenchDays(bench[0])
            .benchPeriods(bench[1])
            .estimatedBenchCost(bench[0] * resolvedAverageRate)
            .projectsByStatus(projectsByStatus)
            .projectsByWorkMode(projectsByWorkMode)
            .lostReasonsBreakdown(lostReasonsBreakdown)
            .dailyRateRanges(dailyRateRanges)
            .sourceRoi(sourceRoi)
            .dailyRateEvolution(dailyRateEvolution)
            .build();
    }

    /**
     * Average asked vs obtained (agreed) daily rate per year of start date, ordered
     * chronologically, so a freelance can see how negotiated rates trend over time.
     */
    private List<DashboardStatsDto.DailyRateEvolution> buildDailyRateEvolution(List<Project> projects) {
        // Per year: [askedSum, askedCount, obtainedSum, obtainedCount, projectCount].
        Map<Integer, long[]> byYear = new TreeMap<>();
        for (Project project : projects) {
            if (project.getStartDate() == null) {
                continue;
            }
            long[] acc = byYear.computeIfAbsent(project.getStartDate().getYear(), k -> new long[5]);
            if (project.getAskedDailyRate() != null) {
                acc[0] += project.getAskedDailyRate();
                acc[1]++;
            }
            if (project.getDailyRate() != null) {
                acc[2] += project.getDailyRate();
                acc[3]++;
            }
            acc[4]++;
        }

        List<DashboardStatsDto.DailyRateEvolution> evolution = new ArrayList<>();
        for (Map.Entry<Integer, long[]> entry : byYear.entrySet()) {
            long[] acc = entry.getValue();
            evolution.add(DashboardStatsDto.DailyRateEvolution.builder()
                .period(String.valueOf(entry.getKey()))
                .averageAskedRate(acc[1] == 0 ? 0 : Math.round((double) acc[0] / acc[1]))
                .averageObtainedRate(acc[3] == 0 ? 0 : Math.round((double) acc[2] / acc[3]))
                .projectCount(acc[4])
                .build());
        }
        return evolution;
    }

    /**
     * Computes idle ("bench") time between consecutive signed missions: missions are the WON
     * projects that carry a start date and duration, ordered chronologically. A gap is counted
     * only when a mission starts after the latest end seen so far, so overlapping or nested
     * missions never produce negative bench. Returns {@code [totalBenchDays, benchPeriods]}.
     */
    private long[] buildBenchStats(List<Project> projects) {
        List<Project> missions = projects.stream()
            .filter(p -> ProjectStatus.WON.equals(p.getStatus())
                && p.getStartDate() != null && p.getDurationInMonths() != null)
            .sorted(Comparator.comparing(Project::getStartDate))
            .toList();

        long totalBenchDays = 0;
        long benchPeriods = 0;
        LocalDate previousEnd = null;
        for (Project mission : missions) {
            LocalDate start = mission.getStartDate();
            LocalDate end = start.plusMonths(mission.getDurationInMonths());
            if (previousEnd != null && start.isAfter(previousEnd)) {
                totalBenchDays += ChronoUnit.DAYS.between(previousEnd, start);
                benchPeriods++;
            }
            if (previousEnd == null || end.isAfter(previousEnd)) {
                previousEnd = end;
            }
        }
        return new long[]{totalBenchDays, benchPeriods};
    }

    /**
     * Ranks each opportunity source by how many of its opportunities turned into signed
     * (WON) contracts. Sorted by signed contracts, then conversion rate, then name.
     */
    private List<DashboardStatsDto.SourceRoi> buildSourceRoiRanking(List<Project> projects) {
        Map<String, long[]> totalsBySource = new LinkedHashMap<>();
        for (Project project : projects) {
            if (project.getSource() == null || project.getSource().getName() == null) {
                continue;
            }
            long[] counts = totalsBySource.computeIfAbsent(project.getSource().getName(), k -> new long[2]);
            counts[0]++;
            if (ProjectStatus.WON.equals(project.getStatus())) {
                counts[1]++;
            }
        }

        return totalsBySource.entrySet().stream()
            .map(entry -> {
                long total = entry.getValue()[0];
                long won = entry.getValue()[1];
                double rate = total == 0 ? 0 : Math.round(won * 1000.0 / total) / 10.0;
                return DashboardStatsDto.SourceRoi.builder()
                    .sourceName(entry.getKey())
                    .totalProjects(total)
                    .wonProjects(won)
                    .conversionRate(rate)
                    .build();
            })
            .sorted(Comparator.comparingLong(DashboardStatsDto.SourceRoi::getWonProjects).reversed()
                .thenComparing(Comparator.comparingDouble(DashboardStatsDto.SourceRoi::getConversionRate).reversed())
                .thenComparing(DashboardStatsDto.SourceRoi::getSourceName))
            .toList();
    }
}
