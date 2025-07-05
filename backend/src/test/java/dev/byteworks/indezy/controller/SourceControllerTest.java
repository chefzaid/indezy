package dev.byteworks.indezy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.byteworks.indezy.dto.SourceDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.model.enums.SourceType;
import dev.byteworks.indezy.service.SourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

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
class SourceControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private SourceService sourceService;

    @Autowired
    private ObjectMapper objectMapper;

    private SourceDto testSourceDto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        testSourceDto = new SourceDto();
        testSourceDto.setId(1L);
        testSourceDto.setName("LinkedIn");
        testSourceDto.setType(SourceType.JOB_BOARD);
        testSourceDto.setLink("https://linkedin.com");
        testSourceDto.setIsListing(false);
        testSourceDto.setPopularityRating(4);
        testSourceDto.setUsefulnessRating(5);
        testSourceDto.setNotes("Professional networking platform");
        testSourceDto.setFreelanceId(1L);
    }

    @Test
    @WithMockUser
    void getAllSources_ShouldReturnSourceList() throws Exception {
        // Given
        List<SourceDto> sources = Arrays.asList(testSourceDto);
        when(sourceService.findAll()).thenReturn(sources);

        // When & Then
        mockMvc.perform(get("/sources"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("LinkedIn")));

        verify(sourceService).findAll();
    }

    @Test
    @WithMockUser
    void getSourceById_WithExistingId_ShouldReturnSource() throws Exception {
        // Given
        when(sourceService.findById(1L)).thenReturn(testSourceDto);

        // When & Then
        mockMvc.perform(get("/sources/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name", is("LinkedIn")));

        verify(sourceService).findById(1L);
    }

    @Test
    @WithMockUser
    void getSourceById_WithNonExistentId_ShouldReturnNotFound() throws Exception {
        // Given
        when(sourceService.findById(999L)).thenThrow(new ResourceNotFoundException("Source not found with id: 999"));

        // When & Then
        mockMvc.perform(get("/sources/999"))
                .andExpect(status().isNotFound());

        verify(sourceService).findById(999L);
    }

    @Test
    @WithMockUser
    void createSource_WithValidData_ShouldCreateAndReturnSource() throws Exception {
        // Given
        when(sourceService.create(any(SourceDto.class))).thenReturn(testSourceDto);

        // When & Then
        mockMvc.perform(post("/sources")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testSourceDto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name", is("LinkedIn")));

        verify(sourceService).create(any(SourceDto.class));
    }

    @Test
    @WithMockUser
    void createSource_WithInvalidData_ShouldReturnValidationError() throws Exception {
        // Given
        SourceDto invalidSource = new SourceDto();
        // Missing required fields

        // When & Then
        mockMvc.perform(post("/sources")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidSource)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    @WithMockUser
    void updateSource_WithValidData_ShouldUpdateAndReturnSource() throws Exception {
        // Given
        testSourceDto.setName("Updated LinkedIn");
        when(sourceService.update(eq(1L), any(SourceDto.class))).thenReturn(testSourceDto);

        // When & Then
        mockMvc.perform(put("/sources/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testSourceDto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name", is("Updated LinkedIn")));

        verify(sourceService).update(eq(1L), any(SourceDto.class));
    }

    @Test
    @WithMockUser
    void deleteSource_WithExistingId_ShouldDeleteSource() throws Exception {
        // Given
        doNothing().when(sourceService).delete(1L);

        // When & Then
        mockMvc.perform(delete("/sources/1")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(sourceService).delete(1L);
    }

    @Test
    @WithMockUser
    void getSourcesByFreelanceId_ShouldReturnSourceList() throws Exception {
        // Given
        List<SourceDto> sources = Arrays.asList(testSourceDto);
        when(sourceService.findByFreelanceId(1L)).thenReturn(sources);

        // When & Then
        mockMvc.perform(get("/sources/by-freelance/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("LinkedIn")));

        verify(sourceService).findByFreelanceId(1L);
    }

    @Test
    @WithMockUser
    void getSourcesByType_ShouldReturnFilteredSources() throws Exception {
        // Given
        List<SourceDto> sources = Arrays.asList(testSourceDto);
        when(sourceService.findByFreelanceIdAndType(1L, SourceType.JOB_BOARD)).thenReturn(sources);

        // When & Then
        mockMvc.perform(get("/sources/by-freelance/1/type/JOB_BOARD"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type", is("JOB_BOARD")));

        verify(sourceService).findByFreelanceIdAndType(1L, SourceType.JOB_BOARD);
    }

    @Test
    @WithMockUser
    void getSourceWithProjects_ShouldReturnSourceWithProjects() throws Exception {
        // Given
        when(sourceService.findByIdWithProjects(1L)).thenReturn(testSourceDto);

        // When & Then
        mockMvc.perform(get("/sources/1/with-projects"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name", is("LinkedIn")));

        verify(sourceService).findByIdWithProjects(1L);
    }

    @Test
    @WithMockUser
    void getAverageRatings_ShouldReturnAverageRatings() throws Exception {
        // Given
        when(sourceService.getAveragePopularityRating(1L)).thenReturn(4.2);
        when(sourceService.getAverageUsefulnessRating(1L)).thenReturn(4.5);

        // When & Then
        mockMvc.perform(get("/sources/by-freelance/1/average-ratings"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.averagePopularityRating", is(4.2)))
                .andExpect(jsonPath("$.averageUsefulnessRating", is(4.5)));

        verify(sourceService).getAveragePopularityRating(1L);
        verify(sourceService).getAverageUsefulnessRating(1L);
    }
}
