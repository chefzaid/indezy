package dev.swirlit.indezy.integration;

import tools.jackson.databind.ObjectMapper;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for Project functionality
 * Tests the complete flow from controller to database
 */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:projecttestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql(scripts = "/project-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @PostConstruct
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void createProject_WithValidData_ShouldCreateAndReturnProject() throws Exception {
        ProjectDto projectDto = new ProjectDto();
        projectDto.setRole("Full Stack Developer");
        projectDto.setDescription("Integration test project");
        projectDto.setTechStack("Java, Spring Boot, Angular");
        projectDto.setDailyRate(600);
        projectDto.setWorkMode(WorkMode.REMOTE);
        projectDto.setStartDate(LocalDate.now());
        projectDto.setDurationInMonths(6);
        projectDto.setFreelanceId(100L);
        projectDto.setClientId(100L);
        projectDto.setSourceId(100L);

        mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(projectDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("Full Stack Developer")))
                .andExpect(jsonPath("$.description", is("Integration test project")))
                .andExpect(jsonPath("$.techStack", is("Java, Spring Boot, Angular")))
                .andExpect(jsonPath("$.dailyRate", is(600)))
                .andExpect(jsonPath("$.workMode", is("REMOTE")))
                .andExpect(jsonPath("$.freelanceId", is(100)))  // Updated to match project-test-data.sql
                .andExpect(jsonPath("$.clientId", is(100)))     // Updated to match project-test-data.sql
                .andExpect(jsonPath("$.sourceId", is(100)))     // Updated to match project-test-data.sql
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    @Test
    void createProject_WithInvalidData_ShouldReturnValidationError() throws Exception {
        ProjectDto projectDto = new ProjectDto();
        // Missing required fields

        mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(projectDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    void getProject_WithExistingId_ShouldReturnProject() throws Exception {
        // First create a project
        ProjectDto projectDto = createValidProjectDto();
        
        String response = mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(projectDto)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ProjectDto createdProject = objectMapper.readValue(response, ProjectDto.class);

        // Then retrieve it
        mockMvc.perform(get("/projects/{id}", createdProject.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdProject.getId().intValue())))
                .andExpect(jsonPath("$.role", is("Full Stack Developer")))
                .andExpect(jsonPath("$.description", is("Integration test project")));
    }

    @Test
    void getProject_WithNonExistentId_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/projects/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Resource Not Found")));
    }

    @Test
    void updateProject_WithValidData_ShouldUpdateAndReturnProject() throws Exception {
        // First create a project
        ProjectDto projectDto = createValidProjectDto();
        
        String response = mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(projectDto)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ProjectDto createdProject = objectMapper.readValue(response, ProjectDto.class);

        // Update the project
        createdProject.setRole("Senior Full Stack Developer");
        createdProject.setDailyRate(700);

        mockMvc.perform(put("/projects/{id}", createdProject.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createdProject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("Senior Full Stack Developer")))
                .andExpect(jsonPath("$.dailyRate", is(700)));
    }

    @Test
    void updateProject_ShouldReplaceItsDocuments() throws Exception {
        ProjectDto projectDto = createValidProjectDto();
        projectDto.setDocuments(List.of("Brief.pdf"));
        ProjectDto created = objectMapper.readValue(mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(projectDto)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(), ProjectDto.class);

        created.setDocuments(List.of("Contract.pdf", "Test brief.pdf"));
        mockMvc.perform(put("/projects/{id}", created.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(created)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documents", contains("Contract.pdf", "Test brief.pdf")));
    }

    @Test
    void createProject_ShouldRefuseATechStackLongerThanItsColumn() throws Exception {
        ProjectDto projectDto = createValidProjectDto();
        projectDto.setTechStack("x".repeat(256));

        mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(projectDto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.techStack").exists());
    }

    @Test
    void deleteProject_WithExistingId_ShouldDeleteProject() throws Exception {
        // First create a project
        ProjectDto projectDto = createValidProjectDto();
        
        String response = mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(projectDto)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ProjectDto createdProject = objectMapper.readValue(response, ProjectDto.class);

        // Delete the project
        mockMvc.perform(delete("/projects/{id}", createdProject.getId()))
                .andExpect(status().isNoContent());

        // Verify it's deleted
        mockMvc.perform(get("/projects/{id}", createdProject.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void endsAWonMissionWithItsReasonKeepsItThroughASaveAndResumesIt() throws Exception {
        ProjectDto mission = createValidProjectDto();
        mission.setStatus(ProjectStatus.WON);
        ProjectDto created = objectMapper.readValue(mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mission)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(), ProjectDto.class);
        String today = LocalDate.now().toString();

        mockMvc.perform(patch("/projects/{id}/end", created.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endedOn\": \"" + today + "\", \"reason\": \"  \"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/projects/{id}/end", created.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endedOn\": \"" + LocalDate.now().minusDays(1) + "\", \"reason\": \"Too early\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/projects/{id}/end", created.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endedOn\": \"" + today + "\", \"reason\": \" The client cut the budget \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endedOn", is(today)))
            .andExpect(jsonPath("$.endReason", is("The client cut the budget")));

        // A save from the form, which does not send the end, keeps it.
        created.setRole("Lead Full Stack Developer");
        mockMvc.perform(put("/projects/{id}", created.getId()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(created)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endedOn", is(today)));

        mockMvc.perform(patch("/projects/{id}/resume", created.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endedOn", nullValue()))
            .andExpect(jsonPath("$.endReason", nullValue()));
    }

    @Test
    void refusesToEndAnOpportunityThatIsNotAWonMission() throws Exception {
        ProjectDto created = objectMapper.readValue(mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createValidProjectDto())))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(), ProjectDto.class);

        mockMvc.perform(patch("/projects/{id}/end", created.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"endedOn\": \"" + LocalDate.now() + "\", \"reason\": \"Done\"}"))
            .andExpect(status().isConflict());
    }

    private ProjectDto createValidProjectDto() {
        ProjectDto projectDto = new ProjectDto();
        projectDto.setRole("Full Stack Developer");
        projectDto.setDescription("Integration test project");
        projectDto.setTechStack("Java, Spring Boot, Angular");
        projectDto.setDailyRate(600);
        projectDto.setWorkMode(WorkMode.REMOTE);
        projectDto.setStartDate(LocalDate.now());
        projectDto.setDurationInMonths(6);
        projectDto.setFreelanceId(100L);  // Updated to match project-test-data.sql
        projectDto.setClientId(100L);     // Updated to match project-test-data.sql
        projectDto.setSourceId(100L);     // Updated to match project-test-data.sql
        return projectDto;
    }
}
