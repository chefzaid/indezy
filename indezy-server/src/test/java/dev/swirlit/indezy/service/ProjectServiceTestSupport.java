package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.mapper.ProjectMapper;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.ClientRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SourceRepository;
import dev.swirlit.indezy.repository.ProjectNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import dev.swirlit.indezy.repository.ActivityReportRepository;
import dev.swirlit.indezy.repository.JobOfferStateRepository;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Mocks and sample records shared by the project service tests. */
@ExtendWith(MockitoExtension.class)
abstract class ProjectServiceTestSupport {

    @Mock
    ProjectRepository projectRepository;

    @Mock
    FreelanceRepository freelanceRepository;

    @Mock
    ClientRepository clientRepository;

    @Mock
    SourceRepository sourceRepository;

    @Mock
    InterviewStepRepository interviewStepRepository;

    @Mock
    ProjectMapper projectMapper;

    @Mock
    SeasonService seasonService;

    @Mock
    ProjectNoteRepository projectNoteRepository;

    @Mock
    ActivityReportRepository activityReportRepository;

    @Mock
    JobOfferStateRepository jobOfferStateRepository;

    @InjectMocks
    ProjectService projectService;

    Project testProject;
    ProjectDto testProjectDto;
    Freelance testFreelance;
    Client testClient;

    @BeforeEach
    void setUp() {
        // Setup test freelance
        testFreelance = new Freelance();
        testFreelance.setId(1L);
        testFreelance.setFirstName("John");
        testFreelance.setLastName("Doe");
        testFreelance.setEmail("john.doe@example.com");
        testFreelance.setStatus(EmploymentStatus.FREELANCE);

        // Setup test client
        testClient = new Client();
        testClient.setId(1L);
        testClient.setCompanyName("Test Company");
        testClient.setCity("Paris");
        testClient.setIsFinal(true);
        testClient.setFreelance(testFreelance);

        // Setup test project
        testProject = new Project();
        testProject.setId(1L);
        testProject.setRole("Full Stack Developer");
        testProject.setDescription("Test project description");
        testProject.setTechStack("Java, Spring Boot, Angular");
        testProject.setDailyRate(600);
        testProject.setWorkMode(WorkMode.HYBRID);
        testProject.setRemoteDaysPerMonth(15);
        testProject.setOnsiteDaysPerMonth(5);
        testProject.setStartDate(LocalDate.of(2024, 1, 15));
        testProject.setDurationInMonths(6);
        testProject.setDaysPerYear(220);
        testProject.setPersonalRating(4);
        testProject.setFreelance(testFreelance);
        testProject.setClient(testClient);

        // Setup test DTO
        testProjectDto = new ProjectDto();
        testProjectDto.setId(1L);
        testProjectDto.setRole("Full Stack Developer");
        testProjectDto.setDescription("Test project description");
        testProjectDto.setTechStack("Java, Spring Boot, Angular");
        testProjectDto.setDailyRate(600);
        testProjectDto.setWorkMode(WorkMode.HYBRID);
        testProjectDto.setRemoteDaysPerMonth(15);
        testProjectDto.setOnsiteDaysPerMonth(5);
        testProjectDto.setStartDate(LocalDate.of(2024, 1, 15));
        testProjectDto.setDurationInMonths(6);
        testProjectDto.setDaysPerYear(220);
        testProjectDto.setPersonalRating(4);
        testProjectDto.setFreelanceId(1L);
        testProjectDto.setClientId(1L);
    }
}
