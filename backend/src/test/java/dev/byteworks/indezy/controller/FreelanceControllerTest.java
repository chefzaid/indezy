package dev.byteworks.indezy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.byteworks.indezy.dto.FreelanceDto;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.model.enums.EmploymentStatus;
import dev.byteworks.indezy.service.FreelanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

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
class FreelanceControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private FreelanceService freelanceService;

    @Autowired
    private ObjectMapper objectMapper;

    private FreelanceDto testFreelanceDto;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        testFreelanceDto = new FreelanceDto();
        testFreelanceDto.setId(1L);
        testFreelanceDto.setFirstName("John");
        testFreelanceDto.setLastName("Doe");
        testFreelanceDto.setEmail("john.doe@example.com");
        testFreelanceDto.setPhone("+33 6 12 34 56 78");
        testFreelanceDto.setBirthDate(LocalDate.of(1990, 5, 15));
        testFreelanceDto.setAddress("123 Rue de la Paix");
        testFreelanceDto.setCity("Paris");
        testFreelanceDto.setStatus(EmploymentStatus.FREELANCE);
        testFreelanceDto.setNoticePeriodInDays(30);
        testFreelanceDto.setAvailabilityDate(LocalDate.now().plusDays(30));
        testFreelanceDto.setReversionRate(0.15);
        testFreelanceDto.setFullName("John Doe");
        testFreelanceDto.setTotalProjects(3);
        testFreelanceDto.setAverageDailyRate(575.0);
    }

    @Test
    @WithMockUser
    void getAllFreelances_ShouldReturnFreelancesList() throws Exception {
        // Given
        List<FreelanceDto> freelances = Arrays.asList(testFreelanceDto);
        when(freelanceService.findAll()).thenReturn(freelances);

        // When & Then
        mockMvc.perform(get("/freelances"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].firstName", is("John")))
                .andExpect(jsonPath("$[0].lastName", is("Doe")))
                .andExpect(jsonPath("$[0].email", is("john.doe@example.com")))
                .andExpect(jsonPath("$[0].status", is("FREELANCE")))
                .andExpect(jsonPath("$[0].fullName", is("John Doe")))
                .andExpect(jsonPath("$[0].totalProjects", is(3)))
                .andExpect(jsonPath("$[0].averageDailyRate", is(575.0)));

        verify(freelanceService).findAll();
    }

    @Test
    @WithMockUser
    void getFreelanceById_WhenFreelanceExists_ShouldReturnFreelance() throws Exception {
        // Given
        when(freelanceService.findById(1L)).thenReturn(testFreelanceDto);

        // When & Then
        mockMvc.perform(get("/freelances/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.lastName", is("Doe")))
                .andExpect(jsonPath("$.email", is("john.doe@example.com")))
                .andExpect(jsonPath("$.phone", is("+33 6 12 34 56 78")))
                .andExpect(jsonPath("$.city", is("Paris")))
                .andExpect(jsonPath("$.status", is("FREELANCE")));

        verify(freelanceService).findById(1L);
    }

    @Test
    @WithMockUser
    void getFreelanceById_WhenFreelanceNotExists_ShouldReturn404() throws Exception {
        // Given
        when(freelanceService.findById(1L)).thenThrow(new ResourceNotFoundException("Freelance not found with id: 1"));

        // When & Then
        mockMvc.perform(get("/freelances/1"))
                .andExpect(status().isNotFound());

        verify(freelanceService).findById(1L);
    }

    @Test
    @WithMockUser
    void getFreelanceByIdWithProjects_WhenFreelanceExists_ShouldReturnFreelanceWithProjects() throws Exception {
        // Given
        when(freelanceService.findByIdWithProjects(1L)).thenReturn(testFreelanceDto);

        // When & Then
        mockMvc.perform(get("/freelances/1/with-projects"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.totalProjects", is(3)))
                .andExpect(jsonPath("$.averageDailyRate", is(575.0)));

        verify(freelanceService).findByIdWithProjects(1L);
    }

    @Test
    @WithMockUser
    void getFreelanceByEmail_WhenFreelanceExists_ShouldReturnFreelance() throws Exception {
        // Given
        when(freelanceService.findByEmail("john.doe@example.com")).thenReturn(testFreelanceDto);

        // When & Then
        mockMvc.perform(get("/freelances/by-email")
                        .param("email", "john.doe@example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.email", is("john.doe@example.com")))
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.lastName", is("Doe")));

        verify(freelanceService).findByEmail("john.doe@example.com");
    }

    @Test
    @WithMockUser
    void getFreelanceByEmail_WhenFreelanceNotExists_ShouldReturn404() throws Exception {
        // Given
        when(freelanceService.findByEmail("nonexistent@example.com"))
                .thenThrow(new ResourceNotFoundException("Freelance not found with email: nonexistent@example.com"));

        // When & Then
        mockMvc.perform(get("/freelances/by-email")
                        .param("email", "nonexistent@example.com"))
                .andExpect(status().isNotFound());

        verify(freelanceService).findByEmail("nonexistent@example.com");
    }

    @Test
    @WithMockUser
    void createFreelance_WithValidData_ShouldCreateFreelance() throws Exception {
        // Given
        when(freelanceService.create(any(FreelanceDto.class))).thenReturn(testFreelanceDto);

        // When & Then
        mockMvc.perform(post("/freelances")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testFreelanceDto)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.lastName", is("Doe")))
                .andExpect(jsonPath("$.email", is("john.doe@example.com")))
                .andExpect(jsonPath("$.status", is("FREELANCE")));

        verify(freelanceService).create(any(FreelanceDto.class));
    }

    @Test
    @WithMockUser
    void createFreelance_WithInvalidData_ShouldReturn400() throws Exception {
        // Given
        FreelanceDto invalidFreelance = new FreelanceDto();
        // Missing required fields: firstName, lastName, email, status

        // When & Then
        mockMvc.perform(post("/freelances")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidFreelance)))
                .andExpect(status().isBadRequest());

        verify(freelanceService, never()).create(any(FreelanceDto.class));
    }

    @Test
    @WithMockUser
    void createFreelance_WithExistingEmail_ShouldReturn400() throws Exception {
        // Given
        when(freelanceService.create(any(FreelanceDto.class)))
                .thenThrow(new IllegalArgumentException("Freelance already exists with email: john.doe@example.com"));

        // When & Then
        mockMvc.perform(post("/freelances")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testFreelanceDto)))
                .andExpect(status().isBadRequest());

        verify(freelanceService).create(any(FreelanceDto.class));
    }

    @Test
    @WithMockUser
    void updateFreelance_WithValidData_ShouldUpdateFreelance() throws Exception {
        // Given
        when(freelanceService.update(eq(1L), any(FreelanceDto.class))).thenReturn(testFreelanceDto);

        // When & Then
        mockMvc.perform(put("/freelances/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testFreelanceDto)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.lastName", is("Doe")));

        verify(freelanceService).update(eq(1L), any(FreelanceDto.class));
    }

    @Test
    @WithMockUser
    void updateFreelance_WhenFreelanceNotExists_ShouldReturn404() throws Exception {
        // Given
        when(freelanceService.update(eq(1L), any(FreelanceDto.class)))
                .thenThrow(new ResourceNotFoundException("Freelance not found with id: 1"));

        // When & Then
        mockMvc.perform(put("/freelances/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testFreelanceDto)))
                .andExpect(status().isNotFound());

        verify(freelanceService).update(eq(1L), any(FreelanceDto.class));
    }

    @Test
    @WithMockUser
    void deleteFreelance_WhenFreelanceExists_ShouldDeleteFreelance() throws Exception {
        // Given
        doNothing().when(freelanceService).delete(1L);

        // When & Then
        mockMvc.perform(delete("/freelances/1")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(freelanceService).delete(1L);
    }

    @Test
    @WithMockUser
    void deleteFreelance_WhenFreelanceNotExists_ShouldReturn404() throws Exception {
        // Given
        doThrow(new ResourceNotFoundException("Freelance not found with id: 1"))
                .when(freelanceService).delete(1L);

        // When & Then
        mockMvc.perform(delete("/freelances/1")
                        .with(csrf()))
                .andExpect(status().isNotFound());

        verify(freelanceService).delete(1L);
    }
}
