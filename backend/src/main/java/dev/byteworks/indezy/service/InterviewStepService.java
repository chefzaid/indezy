package dev.byteworks.indezy.service;

import dev.byteworks.indezy.dto.InterviewStepDto;
import dev.byteworks.indezy.dto.KanbanBoardDto;
import dev.byteworks.indezy.dto.StepTransitionDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.mapper.InterviewStepMapper;
import dev.byteworks.indezy.model.InterviewStep;
import dev.byteworks.indezy.model.Project;
import dev.byteworks.indezy.model.enums.StepStatus;
import dev.byteworks.indezy.repository.InterviewStepRepository;
import dev.byteworks.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InterviewStepService {

    private final InterviewStepRepository interviewStepRepository;
    private final ProjectRepository projectRepository;
    private final InterviewStepMapper interviewStepMapper;

    @Transactional(readOnly = true)
    public List<InterviewStepDto> findAll() {
        log.debug("Finding all interview steps");
        return interviewStepRepository.findAll()
            .stream()
            .map(interviewStepMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public InterviewStepDto findById(Long id) {
        log.debug("Finding interview step by id: {}", id);
        InterviewStep interviewStep = interviewStepRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format("Interview step not found with id: %d", id)));
        return interviewStepMapper.toDto(interviewStep);
    }

    @Transactional(readOnly = true)
    public List<InterviewStepDto> findByProjectId(Long projectId) {
        log.debug("Finding interview steps by project id: {}", projectId);
        return interviewStepRepository.findByProjectId(projectId)
            .stream()
            .map(interviewStepMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewStepDto> findByProjectIdOrderByDate(Long projectId) {
        log.debug("Finding interview steps by project id ordered by date: {}", projectId);
        return interviewStepRepository.findByProjectIdOrderByDate(projectId)
            .stream()
            .map(interviewStepMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewStepDto> findByFreelanceIdAndStatus(Long freelanceId, StepStatus status) {
        log.debug("Finding interview steps by freelance id: {} and status: {}", freelanceId, status);
        return interviewStepRepository.findByFreelanceIdAndStatus(freelanceId, status)
            .stream()
            .map(interviewStepMapper::toDto)
            .toList();
    }

    public InterviewStepDto create(InterviewStepDto interviewStepDto) {
        log.debug("Creating new interview step for project: {}", interviewStepDto.getProjectId());
        
        // Validate project exists
        Project project = projectRepository.findById(interviewStepDto.getProjectId())
            .orElseThrow(() -> new ResourceNotFoundException(String.format("Project not found with id: %d", interviewStepDto.getProjectId())));
        
        InterviewStep interviewStep = interviewStepMapper.toEntity(interviewStepDto);
        interviewStep.setProject(project);
        
        InterviewStep savedInterviewStep = interviewStepRepository.save(interviewStep);
        
        log.info("Created interview step with id: {}", savedInterviewStep.getId());
        return interviewStepMapper.toDto(savedInterviewStep);
    }

    public InterviewStepDto update(Long id, InterviewStepDto interviewStepDto) {
        log.debug("Updating interview step with id: {}", id);
        
        InterviewStep existingInterviewStep = interviewStepRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format("Interview step not found with id: %d", id)));

        interviewStepMapper.updateEntity(interviewStepDto, existingInterviewStep);
        InterviewStep updatedInterviewStep = interviewStepRepository.save(existingInterviewStep);
        
        log.info("Updated interview step with id: {}", updatedInterviewStep.getId());
        return interviewStepMapper.toDto(updatedInterviewStep);
    }

    public void delete(Long id) {
        log.debug("Deleting interview step with id: {}", id);
        
        if (!interviewStepRepository.existsById(id)) {
            throw new ResourceNotFoundException(String.format("Interview step not found with id: %d", id));
        }

        interviewStepRepository.deleteById(id);
        log.info("Deleted interview step with id: {}", id);
    }

    public InterviewStepDto updateStatus(Long id, StepStatus status) {
        log.debug("Updating interview step status with id: {} to status: {}", id, status);
        
        InterviewStep interviewStep = interviewStepRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format("Interview step not found with id: %d", id)));

        interviewStep.setStatus(status);
        InterviewStep updatedInterviewStep = interviewStepRepository.save(interviewStep);
        
        log.info("Updated interview step status with id: {} to status: {}", id, status);
        return interviewStepMapper.toDto(updatedInterviewStep);
    }

    public InterviewStepDto scheduleStep(Long id, LocalDateTime date) {
        log.debug("Scheduling interview step with id: {} for date: {}", id, date);
        
        InterviewStep interviewStep = interviewStepRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format("Interview step not found with id: %d", id)));

        interviewStep.setDate(date);
        interviewStep.setStatus(StepStatus.PLANNED);
        InterviewStep updatedInterviewStep = interviewStepRepository.save(interviewStep);
        
        log.info("Scheduled interview step with id: {} for date: {}", id, date);
        return interviewStepMapper.toDto(updatedInterviewStep);
    }

    public InterviewStepDto markAsWaitingFeedback(Long id) {
        log.debug("Marking interview step as waiting feedback with id: {}", id);
        return updateStatus(id, StepStatus.WAITING_FEEDBACK);
    }

    public InterviewStepDto markAsValidated(Long id) {
        log.debug("Marking interview step as validated with id: {}", id);
        return updateStatus(id, StepStatus.VALIDATED);
    }

    public InterviewStepDto markAsFailed(Long id) {
        log.debug("Marking interview step as failed with id: {}", id);
        return updateStatus(id, StepStatus.FAILED);
    }

    public InterviewStepDto markAsCanceled(Long id) {
        log.debug("Marking interview step as canceled with id: {}", id);
        return updateStatus(id, StepStatus.CANCELED);
    }

    // Kanban board specific methods
    private static final List<String> INTERVIEW_STEPS_ORDER = Arrays.asList(
        "Prise de Contact",
        "Entretien Commercial",
        "Positionnement",
        "Test Technique",
        "Entretien Technique",
        "Entretien Manager",
        "Validation"
    );

    @Transactional(readOnly = true)
    public KanbanBoardDto getKanbanBoard(Long freelanceId) {
        log.debug("Getting kanban board for freelance: {}", freelanceId);

        // Get all projects for the freelance
        List<Project> projects = projectRepository.findByFreelanceId(freelanceId);

        // Initialize columns map
        Map<String, List<KanbanBoardDto.ProjectCardDto>> columns = new LinkedHashMap<>();
        for (String step : INTERVIEW_STEPS_ORDER) {
            columns.put(step, new ArrayList<>());
        }

        // Process each project
        for (Project project : projects) {
            List<InterviewStep> steps = interviewStepRepository.findByProjectIdOrderByDate(project.getId());

            // Find the current active step (first non-validated step or last step if all validated)
            InterviewStep currentStep = findCurrentStep(steps);

            if (currentStep != null) {
                KanbanBoardDto.ProjectCardDto card = createProjectCard(project, currentStep, steps);
                String stepTitle = currentStep.getTitle();
                if (columns.containsKey(stepTitle)) {
                    columns.get(stepTitle).add(card);
                }
            }
        }

        KanbanBoardDto kanbanBoard = new KanbanBoardDto();
        kanbanBoard.setColumns(columns);
        kanbanBoard.setStepOrder(INTERVIEW_STEPS_ORDER);

        return kanbanBoard;
    }

    private InterviewStep findCurrentStep(List<InterviewStep> steps) {
        // Find first step that is not validated
        for (InterviewStep step : steps) {
            if (!step.isCompleted()) {
                return step;
            }
        }
        // If all steps are validated, return the last step
        return steps.isEmpty() ? null : steps.get(steps.size() - 1);
    }

    private KanbanBoardDto.ProjectCardDto createProjectCard(Project project, InterviewStep currentStep, List<InterviewStep> allSteps) {
        KanbanBoardDto.ProjectCardDto card = new KanbanBoardDto.ProjectCardDto();
        card.setProjectId(project.getId());
        card.setRole(project.getRole());
        card.setClientName(project.getClient() != null ? project.getClient().getCompanyName() : null);
        card.setDailyRate(project.getDailyRate());
        card.setWorkMode(project.getWorkMode() != null ? project.getWorkMode().toString() : null);
        card.setTechStack(project.getTechStack());
        card.setCurrentStepTitle(currentStep.getTitle());
        card.setCurrentStepStatus(currentStep.getStatus().toString());
        card.setCurrentStepDate(currentStep.getDate() != null ? currentStep.getDate().toString() : null);
        card.setNotes(currentStep.getNotes());

        // Calculate step statistics
        card.setTotalSteps(allSteps.size());
        card.setCompletedSteps((int) allSteps.stream().filter(InterviewStep::isCompleted).count());
        card.setFailedSteps((int) allSteps.stream().filter(InterviewStep::isFailed).count());

        return card;
    }

    public InterviewStepDto transitionProjectToNextStep(StepTransitionDto transitionDto) {
        log.debug("Transitioning project {} from {} to {}",
            transitionDto.getProjectId(), transitionDto.getFromStepTitle(), transitionDto.getToStepTitle());

        // Find the current step to validate
        List<InterviewStep> currentSteps = interviewStepRepository.findByProjectId(transitionDto.getProjectId());
        InterviewStep fromStep = currentSteps.stream()
            .filter(step -> step.getTitle().equals(transitionDto.getFromStepTitle()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(
                String.format("Step '%s' not found for project %d",
                    transitionDto.getFromStepTitle(), transitionDto.getProjectId())));

        // Mark the previous step as validated
        fromStep.setStatus(StepStatus.VALIDATED);
        interviewStepRepository.save(fromStep);

        // Find or create the next step
        InterviewStep toStep = currentSteps.stream()
            .filter(step -> step.getTitle().equals(transitionDto.getToStepTitle()))
            .findFirst()
            .orElse(null);

        if (toStep == null) {
            // Create new step if it doesn't exist
            Project project = projectRepository.findById(transitionDto.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    String.format("Project not found with id: %d", transitionDto.getProjectId())));

            toStep = new InterviewStep();
            toStep.setTitle(transitionDto.getToStepTitle());
            toStep.setStatus(StepStatus.TO_PLAN);
            toStep.setProject(project);
            if (transitionDto.getNotes() != null) {
                toStep.setNotes(transitionDto.getNotes());
            }
        } else {
            // Update existing step
            toStep.setStatus(StepStatus.TO_PLAN);
            if (transitionDto.getNotes() != null) {
                toStep.setNotes(transitionDto.getNotes());
            }
        }

        InterviewStep savedStep = interviewStepRepository.save(toStep);
        log.info("Transitioned project {} to step: {}", transitionDto.getProjectId(), transitionDto.getToStepTitle());

        return interviewStepMapper.toDto(savedStep);
    }
}
