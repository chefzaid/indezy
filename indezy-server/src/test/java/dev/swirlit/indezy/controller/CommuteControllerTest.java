package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.CommuteInfoDto;
import dev.swirlit.indezy.dto.ProjectCommuteDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.enums.TravelMode;
import dev.swirlit.indezy.service.CommuteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureWebMvc
@WithMockUser
@ActiveProfiles("test")
class CommuteControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private CommuteService commuteService;

    private ProjectCommuteDto testProjectCommuteDto;
    private CommuteInfoDto testCommuteInfoDto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        ProjectDto projectDto = new ProjectDto();
        projectDto.setId(1L);
        projectDto.setRole("Full Stack Developer");

        testCommuteInfoDto = CommuteInfoDto.builder()
                .projectId(1L)
                .projectRole("Full Stack Developer")
                .clientName("Test Company")
                .origin("Paris")
                .destination("Versailles")
                .travelMode(TravelMode.DRIVING)
                .durationInSeconds(1800)
                .durationText("30 min")
                .build();

        testProjectCommuteDto = ProjectCommuteDto.builder()
                .project(projectDto)
                .commute(testCommuteInfoDto)
                .build();
    }

    @Test
    void getProjectsSortedByCommute_WithDefaultMode_ShouldReturnSortedProjects() throws Exception {
        when(commuteService.getProjectsSortedByCommute(1L, TravelMode.DRIVING))
                .thenReturn(List.of(testProjectCommuteDto));

        mockMvc.perform(get("/commute/projects/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].project.role", is("Full Stack Developer")))
                .andExpect(jsonPath("$[0].commute.durationInSeconds", is(1800)));

        verify(commuteService).getProjectsSortedByCommute(1L, TravelMode.DRIVING);
    }

    @Test
    void getProjectsSortedByCommute_WithTransitMode_ShouldPassModeThrough() throws Exception {
        when(commuteService.getProjectsSortedByCommute(1L, TravelMode.TRANSIT))
                .thenReturn(List.of());

        mockMvc.perform(get("/commute/projects/1").param("travelMode", "TRANSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(commuteService).getProjectsSortedByCommute(1L, TravelMode.TRANSIT);
    }

    @Test
    void getProjectsSortedByCommute_WithUnknownFreelance_ShouldReturnNotFound() throws Exception {
        when(commuteService.getProjectsSortedByCommute(99L, TravelMode.DRIVING))
                .thenThrow(new ResourceNotFoundException("Freelance not found: 99"));

        mockMvc.perform(get("/commute/projects/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCommuteForProject_ShouldReturnCommuteInfo() throws Exception {
        when(commuteService.getCommuteForProject(1L, 1L, TravelMode.DRIVING))
                .thenReturn(testCommuteInfoDto);

        mockMvc.perform(get("/commute/projects/1/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId", is(1)))
                .andExpect(jsonPath("$.durationText", is("30 min")));

        verify(commuteService).getCommuteForProject(1L, 1L, TravelMode.DRIVING);
    }

    @Test
    void getCommuteForProject_WithUnknownProject_ShouldReturnNotFound() throws Exception {
        when(commuteService.getCommuteForProject(1L, 99L, TravelMode.DRIVING))
                .thenThrow(new ResourceNotFoundException("Project not found: 99"));

        mockMvc.perform(get("/commute/projects/1/99"))
                .andExpect(status().isNotFound());
    }
}
