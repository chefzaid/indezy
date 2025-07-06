package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.ProjectDto;
import dev.byteworks.indezy.model.*;
import dev.byteworks.indezy.model.enums.EmploymentStatus;
import dev.byteworks.indezy.model.enums.WorkMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ProjectMapper
 * Tests mapping between Project entities and ProjectDto
 */
class ProjectMapperTest {

    private ProjectMapper projectMapper;
    private Project testProject;
    private ProjectDto testProjectDto;
    private Freelance testFreelance;
    private Client testClient;
    private Source testSource;

    @BeforeEach
    void setUp() {
        InterviewStepMapper interviewStepMapper = new InterviewStepMapper();
        projectMapper = new ProjectMapper(interviewStepMapper);

        // Create test freelance
        testFreelance = new Freelance();
        testFreelance.setId(1L);
        testFreelance.setFirstName("John");
        testFreelance.setLastName("Doe");
        testFreelance.setEmail("john.doe@example.com");
        testFreelance.setStatus(EmploymentStatus.FREELANCE);

        // Create test client
        testClient = new Client();
        testClient.setId(2L);
        testClient.setCompanyName("Test Client");
        testClient.setAddress("123 Test St");
        testClient.setCity("Test City");
        testClient.setIsFinal(true);

        // Create test source
        testSource = new Source();
        testSource.setId(3L);
        testSource.setName("Test Source");

        // Create test project
        testProject = new Project();
        testProject.setId(1L);
        testProject.setRole("Full Stack Developer");
        testProject.setDescription("Test project description");
        testProject.setTechStack("Java, Spring Boot, Angular");
        testProject.setDailyRate(600);
        testProject.setWorkMode(WorkMode.REMOTE);
        testProject.setStartDate(LocalDate.of(2024, 1, 1));
        testProject.setDurationInMonths(6);
        testProject.setFreelance(testFreelance);
        testProject.setClient(testClient);
        testProject.setSource(testSource);

        // Add steps (simplified for testing)
        testProject.setSteps(new ArrayList<>());

        // Create test ProjectDto
        testProjectDto = new ProjectDto();
        testProjectDto.setId(1L);
        testProjectDto.setRole("Full Stack Developer");
        testProjectDto.setDescription("Test project description");
        testProjectDto.setTechStack("Java, Spring Boot, Angular");
        testProjectDto.setDailyRate(600);
        testProjectDto.setWorkMode(WorkMode.REMOTE);
        testProjectDto.setStartDate(LocalDate.of(2024, 1, 1));
        testProjectDto.setDurationInMonths(6);
        testProjectDto.setFreelanceId(1L);
        testProjectDto.setClientId(2L);
        testProjectDto.setSourceId(3L);
    }

    @Test
    void toDto_WithValidProject_ShouldMapToProjectDto() {
        ProjectDto result = projectMapper.toDto(testProject);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testProject.getId());
        assertThat(result.getRole()).isEqualTo(testProject.getRole());
        assertThat(result.getDescription()).isEqualTo(testProject.getDescription());
        assertThat(result.getTechStack()).isEqualTo(testProject.getTechStack());
        assertThat(result.getDailyRate()).isEqualTo(testProject.getDailyRate());
        assertThat(result.getWorkMode()).isEqualTo(testProject.getWorkMode());
        assertThat(result.getStartDate()).isEqualTo(testProject.getStartDate());
        assertThat(result.getDurationInMonths()).isEqualTo(testProject.getDurationInMonths());
        assertThat(result.getFreelanceId()).isEqualTo(testProject.getFreelance().getId());
        assertThat(result.getClientId()).isEqualTo(testProject.getClient().getId());
        assertThat(result.getSourceId()).isEqualTo(testProject.getSource().getId());
    }

    @Test
    void toDto_WithNullProject_ShouldReturnNull() {
        ProjectDto result = projectMapper.toDto(null);

        assertThat(result).isNull();
    }

    @Test
    void toEntity_WithValidProjectDto_ShouldMapToProject() {
        Project result = projectMapper.toEntity(testProjectDto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testProjectDto.getId());
        assertThat(result.getRole()).isEqualTo(testProjectDto.getRole());
        assertThat(result.getDescription()).isEqualTo(testProjectDto.getDescription());
        assertThat(result.getTechStack()).isEqualTo(testProjectDto.getTechStack());
        assertThat(result.getDailyRate()).isEqualTo(testProjectDto.getDailyRate());
        assertThat(result.getWorkMode()).isEqualTo(testProjectDto.getWorkMode());
        assertThat(result.getStartDate()).isEqualTo(testProjectDto.getStartDate());
        assertThat(result.getDurationInMonths()).isEqualTo(testProjectDto.getDurationInMonths());
        // Note: Relations are not mapped in toEntity, they should be set separately
        assertThat(result.getFreelance()).isNull();
        assertThat(result.getClient()).isNull();
        assertThat(result.getSource()).isNull();
    }

    @Test
    void toEntity_WithNullProjectDto_ShouldReturnNull() {
        Project result = projectMapper.toEntity(null);

        assertThat(result).isNull();
    }

    @Test
    void updateEntity_WithValidData_ShouldUpdateProject() {
        Project existingProject = new Project();
        existingProject.setId(1L);
        existingProject.setRole("Old Role");
        existingProject.setDescription("Old Description");

        projectMapper.updateEntity(testProjectDto, existingProject);

        assertThat(existingProject.getRole()).isEqualTo(testProjectDto.getRole());
        assertThat(existingProject.getDescription()).isEqualTo(testProjectDto.getDescription());
        assertThat(existingProject.getTechStack()).isEqualTo(testProjectDto.getTechStack());
        assertThat(existingProject.getDailyRate()).isEqualTo(testProjectDto.getDailyRate());
        assertThat(existingProject.getWorkMode()).isEqualTo(testProjectDto.getWorkMode());
        assertThat(existingProject.getStartDate()).isEqualTo(testProjectDto.getStartDate());
        assertThat(existingProject.getDurationInMonths()).isEqualTo(testProjectDto.getDurationInMonths());
    }

    @Test
    void updateEntity_WithNullDto_ShouldNotUpdateProject() {
        Project existingProject = new Project();
        existingProject.setId(1L);
        existingProject.setRole("Original Role");

        projectMapper.updateEntity(null, existingProject);

        assertThat(existingProject.getRole()).isEqualTo("Original Role");
    }

    @Test
    void updateEntity_WithNullEntity_ShouldNotThrowException() {
        // This should not throw an exception - just verify it completes without error
        projectMapper.updateEntity(testProjectDto, null);

        // If we reach this point, no exception was thrown
        assertThat(testProjectDto).isNotNull(); // Simple assertion to satisfy test requirements
    }
}
