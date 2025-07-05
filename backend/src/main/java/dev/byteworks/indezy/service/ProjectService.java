package dev.byteworks.indezy.service;

import dev.byteworks.indezy.dto.ProjectDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.mapper.ProjectMapper;
import dev.byteworks.indezy.model.Client;
import dev.byteworks.indezy.model.Freelance;
import dev.byteworks.indezy.model.Project;
import dev.byteworks.indezy.model.Source;
import dev.byteworks.indezy.model.enums.WorkMode;
import dev.byteworks.indezy.repository.ClientRepository;
import dev.byteworks.indezy.repository.FreelanceRepository;
import dev.byteworks.indezy.repository.ProjectRepository;
import dev.byteworks.indezy.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final FreelanceRepository freelanceRepository;
    private final ClientRepository clientRepository;
    private final SourceRepository sourceRepository;
    private final ProjectMapper projectMapper;

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
            .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + id));
        return projectMapper.toDto(project);
    }

    @Transactional(readOnly = true)
    public ProjectDto findByIdWithSteps(Long id) {
        log.debug("Finding project with steps by id: {}", id);
        Project project = projectRepository.findByIdWithSteps(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + id));
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
                           p.getTechStack().toLowerCase().contains(techStack.toLowerCase()))
                .toList();
        }
        
        return projects.stream()
            .map(projectMapper::toDto)
            .toList();
    }

    public ProjectDto create(ProjectDto projectDto) {
        log.debug("Creating new project: {}", projectDto.getRole());
        
        Project project = projectMapper.toEntity(projectDto);
        
        // Set required relationships
        if (projectDto.getFreelanceId() != null) {
            Freelance freelance = freelanceRepository.findById(projectDto.getFreelanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Freelance not found with id: " + projectDto.getFreelanceId()));
            project.setFreelance(freelance);
        }
        
        if (projectDto.getClientId() != null) {
            Client client = clientRepository.findById(projectDto.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + projectDto.getClientId()));
            project.setClient(client);
        }
        
        if (projectDto.getMiddlemanId() != null) {
            Client middleman = clientRepository.findById(projectDto.getMiddlemanId())
                .orElseThrow(() -> new ResourceNotFoundException("Middleman not found with id: " + projectDto.getMiddlemanId()));
            project.setMiddleman(middleman);
        }
        
        if (projectDto.getSourceId() != null) {
            Source source = sourceRepository.findById(projectDto.getSourceId())
                .orElseThrow(() -> new ResourceNotFoundException("Source not found with id: " + projectDto.getSourceId()));
            project.setSource(source);
        }
        
        Project savedProject = projectRepository.save(project);
        
        log.info("Created project with id: {}", savedProject.getId());
        return projectMapper.toDto(savedProject);
    }

    public ProjectDto update(Long id, ProjectDto projectDto) {
        log.debug("Updating project with id: {}", id);
        
        Project existingProject = projectRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + id));

        projectMapper.updateEntity(projectDto, existingProject);
        
        // Update relationships if provided
        if (projectDto.getClientId() != null && !projectDto.getClientId().equals(existingProject.getClient().getId())) {
            Client client = clientRepository.findById(projectDto.getClientId())
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + projectDto.getClientId()));
            existingProject.setClient(client);
        }
        
        if (projectDto.getMiddlemanId() != null) {
            if (existingProject.getMiddleman() == null || !projectDto.getMiddlemanId().equals(existingProject.getMiddleman().getId())) {
                Client middleman = clientRepository.findById(projectDto.getMiddlemanId())
                    .orElseThrow(() -> new ResourceNotFoundException("Middleman not found with id: " + projectDto.getMiddlemanId()));
                existingProject.setMiddleman(middleman);
            }
        } else {
            existingProject.setMiddleman(null);
        }
        
        if (projectDto.getSourceId() != null) {
            if (existingProject.getSource() == null || !projectDto.getSourceId().equals(existingProject.getSource().getId())) {
                Source source = sourceRepository.findById(projectDto.getSourceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Source not found with id: " + projectDto.getSourceId()));
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
            throw new ResourceNotFoundException("Project not found with id: " + id);
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
}
