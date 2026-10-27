package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.EmploymentStatus;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
abstract class DashboardStatsTestSupport {
    @Mock
    ProjectRepository projectRepository;

    @Mock
    FreelanceRepository freelanceRepository;

    @Mock
    ContactRepository contactRepository;

    @Mock
    InterviewStepRepository interviewStepRepository;

    @Mock
    ContactInteractionService contactInteractionService;

    @Mock
    InvoiceService invoiceService;

    @Mock
    PurchaseOrderService purchaseOrderService;

    @Mock
    ComplianceDocumentService complianceDocumentService;

    @InjectMocks
    DashboardStatsService dashboardStatsService;

    Project testProject;
    Freelance testFreelance;
    Client testClient;

    @BeforeEach
    void setUp() {
        testFreelance = new Freelance();
        testFreelance.setId(1L);
        testFreelance.setFirstName("John");
        testFreelance.setLastName("Doe");
        testFreelance.setEmail("john.doe@example.com");
        testFreelance.setStatus(EmploymentStatus.FREELANCE);

        testClient = new Client();
        testClient.setId(1L);
        testClient.setCompanyName("Test Company");
        testClient.setCity("Paris");
        testClient.setIsFinal(true);
        testClient.setFreelance(testFreelance);

        testProject = new Project();
        testProject.setId(1L);
        testProject.setRole("Full Stack Developer");
        testProject.setDailyRate(600);
        testProject.setWorkMode(WorkMode.HYBRID);
        testProject.setStartDate(LocalDate.of(2024, 1, 15));
        testProject.setDurationInMonths(6);
        testProject.setDaysPerYear(220);
        testProject.setFreelance(testFreelance);
        testProject.setClient(testClient);
    }

    Project projectWithStatus(ProjectStatus status) {
        Project project = new Project();
        project.setStatus(status);
        return project;
    }

    Project wonMission(Long id, LocalDate startDate, Integer durationInMonths) {
        Project project = new Project();
        project.setId(id);
        project.setStatus(ProjectStatus.WON);
        project.setRole("Mission " + id);
        project.setStartDate(startDate);
        project.setDurationInMonths(durationInMonths);
        project.setClient(testClient);
        return project;
    }

    Project activeOpportunity(Long id, ProjectStatus status, LocalDateTime updatedAt) {
        Project project = new Project();
        project.setId(id);
        project.setStatus(status);
        project.setRole("Opportunity " + id);
        project.setClient(testClient);
        project.setUpdatedAt(updatedAt);
        return project;
    }
}
