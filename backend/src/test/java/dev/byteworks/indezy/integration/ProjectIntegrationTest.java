package dev.byteworks.indezy.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.byteworks.indezy.dto.ProjectDto;
import dev.byteworks.indezy.model.enums.WorkMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for Project functionality
 * Tests the complete flow from controller to database
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql("/test-data.sql")
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
        projectDto.setFreelanceId(1L);
        projectDto.setClientId(1L);
        projectDto.setSourceId(1L);

        mockMvc.perform(post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(projectDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role", is("Full Stack Developer")))
                .andExpect(jsonPath("$.description", is("Integration test project")))
                .andExpect(jsonPath("$.techStack", is("Java, Spring Boot, Angular")))
                .andExpect(jsonPath("$.dailyRate", is(600)))
                .andExpect(jsonPath("$.workMode", is("REMOTE")))
                .andExpect(jsonPath("$.freelanceId", is(1)))
                .andExpect(jsonPath("$.clientId", is(1)))
                .andExpect(jsonPath("$.sourceId", is(1)))
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

    private ProjectDto createValidProjectDto() {
        ProjectDto projectDto = new ProjectDto();
        projectDto.setRole("Full Stack Developer");
        projectDto.setDescription("Integration test project");
        projectDto.setTechStack("Java, Spring Boot, Angular");
        projectDto.setDailyRate(600);
        projectDto.setWorkMode(WorkMode.REMOTE);
        projectDto.setStartDate(LocalDate.now());
        projectDto.setDurationInMonths(6);
        projectDto.setFreelanceId(1L);
        projectDto.setClientId(1L);
        projectDto.setSourceId(1L);
        return projectDto;
    }
}
