package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.InterviewStepDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.InterviewStepMapper;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InterviewStepService {

    private static final String STEP_NOT_FOUND_MSG = "Interview step not found with id: %d";

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
            .orElseThrow(() -> new ResourceNotFoundException(String.format(STEP_NOT_FOUND_MSG, id)));
        return interviewStepMapper.toDto(interviewStep);
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
            .orElseThrow(() -> new ResourceNotFoundException(String.format(STEP_NOT_FOUND_MSG, id)));

        interviewStepMapper.updateEntity(interviewStepDto, existingInterviewStep);
        InterviewStep updatedInterviewStep = interviewStepRepository.save(existingInterviewStep);
        
        log.info("Updated interview step with id: {}", updatedInterviewStep.getId());
        return interviewStepMapper.toDto(updatedInterviewStep);
    }

    public void delete(Long id) {
        log.debug("Deleting interview step with id: {}", id);
        
        if (!interviewStepRepository.existsById(id)) {
            throw new ResourceNotFoundException(String.format(STEP_NOT_FOUND_MSG, id));
        }

        interviewStepRepository.deleteById(id);
        log.info("Deleted interview step with id: {}", id);
    }

    public InterviewStepDto updateStatus(Long id, StepStatus status) {
        log.debug("Updating interview step status with id: {} to status: {}", id, status);
        
        InterviewStep interviewStep = interviewStepRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(STEP_NOT_FOUND_MSG, id)));

        interviewStep.setStatus(status);
        InterviewStep updatedInterviewStep = interviewStepRepository.save(interviewStep);
        
        log.info("Updated interview step status with id: {} to status: {}", id, status);
        return interviewStepMapper.toDto(updatedInterviewStep);
    }

}
