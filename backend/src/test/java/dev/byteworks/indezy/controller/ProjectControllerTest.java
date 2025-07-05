package dev.byteworks.indezy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.byteworks.indezy.dto.ProjectDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.model.enums.WorkMode;
import dev.byteworks.indezy.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureWebMvc
@WithMockUser
@ActiveProfiles("test")
class ProjectControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;



    @MockitoBean
    private ProjectService projectService;

    @Autowired
    private ObjectMapper objectMapper;

    private ProjectDto testProjectDto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
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
        testProjectDto.setClientName("Test Company");
        testProjectDto.setTotalRevenue(79200);
    }

    @Test
    @WithMockUser
    void getAllProjects_ShouldReturnProjectsList() throws Exception {
        // Given
        List<ProjectDto> projects = Arrays.asList(testProjectDto);
        when(projectService.findAll()).thenReturn(projects);

        // When & Then
        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].role", is("Full Stack Developer")))
                .andExpect(jsonPath("$[0].dailyRate", is(600)))
                .andExpect(jsonPath("$[0].workMode", is("HYBRID")))
                .andExpect(jsonPath("$[0].clientName", is("Test Company")));

        verify(projectService).findAll();
    }

    @Test
    @WithMockUser
    void getProjectById_WhenProjectExists_ShouldReturnProject() throws Exception {
        // Given
        when(projectService.findById(1L)).thenReturn(testProjectDto);

        // When & Then
        mockMvc.perform(get("/projects/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.role", is("Full Stack Developer")))
                .andExpect(jsonPath("$.dailyRate", is(600)))
                .andExpect(jsonPath("$.techStack", is("Java, Spring Boot, Angular")));

        verify(projectService).findById(1L);
    }

    @Test
    @WithMockUser
    void getProjectById_WhenProjectNotExists_ShouldReturn404() throws Exception {
        // Given
        when(projectService.findById(1L)).thenThrow(new ResourceNotFoundException("Project not found with id: 1"));

        // When & Then
        mockMvc.perform(get("/projects/1"))
                .andExpect(status().isNotFound());

        verify(projectService).findById(1L);
    }

    @Test
    @WithMockUser
    void getProjectsByFreelanceId_ShouldReturnProjectsForFreelance() throws Exception {
        // Given
        List<ProjectDto> projects = Arrays.asList(testProjectDto);
        when(projectService.findByFreelanceId(1L)).thenReturn(projects);

        // When & Then
        mockMvc.perform(get("/projects/by-freelance/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].freelanceId", is(1)));

        verify(projectService).findByFreelanceId(1L);
    }

    @Test
    @WithMockUser
    void getProjectsByFreelanceIdWithFilters_ShouldApplyFilters() throws Exception {
        // Given
        List<ProjectDto> projects = Arrays.asList(testProjectDto);
        when(projectService.findByFreelanceIdAndFilters(eq(1L), eq(500), eq(700), eq(WorkMode.HYBRID), any(LocalDate.class), eq("Java")))
                .thenReturn(projects);

        // When & Then
        mockMvc.perform(get("/projects/by-freelance/1/filtered")
                        .param("minRate", "500")
                        .param("maxRate", "700")
                        .param("workMode", "HYBRID")
                        .param("startDateAfter", "2024-01-01")
                        .param("techStack", "Java"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)));

        verify(projectService).findByFreelanceIdAndFilters(eq(1L), eq(500), eq(700), eq(WorkMode.HYBRID), any(LocalDate.class), eq("Java"));
    }

    @Test
    @WithMockUser
    void createProject_WithValidData_ShouldCreateProject() throws Exception {
        // Given
        when(projectService.create(any(ProjectDto.class))).thenReturn(testProjectDto);

        // When & Then
        mockMvc.perform(post("/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testProjectDto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.role", is("Full Stack Developer")))
                .andExpect(jsonPath("$.dailyRate", is(600)));

        verify(projectService).create(any(ProjectDto.class));
    }

    @Test
    @WithMockUser
    void createProject_WithInvalidData_ShouldReturn400() throws Exception {
        // Given
        ProjectDto invalidProject = new ProjectDto();
        // Missing required fields: role and dailyRate

        // When & Then
        mockMvc.perform(post("/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidProject)))
                .andExpect(status().isBadRequest());

        verify(projectService, never()).create(any(ProjectDto.class));
    }

    @Test
    @WithMockUser
    void updateProject_WithValidData_ShouldUpdateProject() throws Exception {
        // Given
        when(projectService.update(eq(1L), any(ProjectDto.class))).thenReturn(testProjectDto);

        // When & Then
        mockMvc.perform(put("/projects/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testProjectDto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.role", is("Full Stack Developer")));

        verify(projectService).update(eq(1L), any(ProjectDto.class));
    }

    @Test
    @WithMockUser
    void updateProject_WhenProjectNotExists_ShouldReturn404() throws Exception {
        // Given
        when(projectService.update(eq(1L), any(ProjectDto.class)))
                .thenThrow(new ResourceNotFoundException("Project not found with id: 1"));

        // When & Then
        mockMvc.perform(put("/projects/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testProjectDto)))
                .andExpect(status().isNotFound());

        verify(projectService).update(eq(1L), any(ProjectDto.class));
    }

    @Test
    @WithMockUser
    void deleteProject_WhenProjectExists_ShouldDeleteProject() throws Exception {
        // Given
        doNothing().when(projectService).delete(1L);

        // When & Then
        mockMvc.perform(delete("/projects/1")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(projectService).delete(1L);
    }

    @Test
    @WithMockUser
    void deleteProject_WhenProjectNotExists_ShouldReturn404() throws Exception {
        // Given
        doThrow(new ResourceNotFoundException("Project not found with id: 1"))
                .when(projectService).delete(1L);

        // When & Then
        mockMvc.perform(delete("/projects/1")
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(projectService).delete(1L);
    }

    @Test
    @WithMockUser
    void getAverageDailyRate_ShouldReturnAverageRate() throws Exception {
        // Given
        when(projectService.getAverageDailyRateByFreelanceId(1L)).thenReturn(575.0);

        // When & Then
        mockMvc.perform(get("/projects/stats/average-rate/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", is(575.0)));

        verify(projectService).getAverageDailyRateByFreelanceId(1L);
    }

    @Test
    @WithMockUser
    void getProjectCount_ShouldReturnProjectCount() throws Exception {
        // Given
        when(projectService.countByFreelanceId(1L)).thenReturn(3L);

        // When & Then
        mockMvc.perform(get("/projects/stats/count/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", is(3)));

        verify(projectService).countByFreelanceId(1L);
    }
}
