package dev.byteworks.indezy.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.byteworks.indezy.dto.FreelanceDto;
import dev.byteworks.indezy.model.enums.EmploymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import jakarta.annotation.PostConstruct;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for Freelance functionality
 * Tests the complete flow from controller to database
 */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
class FreelanceIntegrationTest {

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
    void createFreelance_WithValidData_ShouldCreateAndReturnFreelance() throws Exception {
        FreelanceDto freelanceDto = new FreelanceDto();
        freelanceDto.setFirstName("John");
        freelanceDto.setLastName("Doe");
        freelanceDto.setEmail("john.doe@example.com");
        freelanceDto.setPhone("123-456-7890");
        freelanceDto.setStatus(EmploymentStatus.FREELANCE);

        mockMvc.perform(post("/freelances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(freelanceDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.lastName", is("Doe")))
                .andExpect(jsonPath("$.email", is("john.doe@example.com")))
                .andExpect(jsonPath("$.phone", is("123-456-7890")))
                .andExpect(jsonPath("$.status", is("FREELANCE")))
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    @Test
    void createFreelance_WithInvalidData_ShouldReturnValidationError() throws Exception {
        FreelanceDto freelanceDto = new FreelanceDto();
        // Missing required fields

        mockMvc.perform(post("/freelances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(freelanceDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.validationErrors").exists());
    }

    @Test
    void getFreelance_WithExistingId_ShouldReturnFreelance() throws Exception {
        // First create a freelance
        FreelanceDto freelanceDto = createValidFreelanceDto();
        
        String response = mockMvc.perform(post("/freelances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(freelanceDto)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        FreelanceDto createdFreelance = objectMapper.readValue(response, FreelanceDto.class);

        // Then retrieve it
        mockMvc.perform(get("/freelances/{id}", createdFreelance.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(createdFreelance.getId().intValue())))
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.lastName", is("Doe")))
                .andExpect(jsonPath("$.email", is("john.doe@example.com")));
    }

    @Test
    void getFreelance_WithNonExistentId_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/freelances/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Resource Not Found")));
    }

    @Test
    void updateFreelance_WithValidData_ShouldUpdateAndReturnFreelance() throws Exception {
        // First create a freelance
        FreelanceDto freelanceDto = createValidFreelanceDto();
        
        String response = mockMvc.perform(post("/freelances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(freelanceDto)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        FreelanceDto createdFreelance = objectMapper.readValue(response, FreelanceDto.class);

        // Update the freelance
        createdFreelance.setFirstName("Jane");

        mockMvc.perform(put("/freelances/{id}", createdFreelance.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createdFreelance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Jane")));
    }

    @Test
    void deleteFreelance_WithExistingId_ShouldDeleteFreelance() throws Exception {
        // First create a freelance
        FreelanceDto freelanceDto = createValidFreelanceDto();
        
        String response = mockMvc.perform(post("/freelances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(freelanceDto)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        FreelanceDto createdFreelance = objectMapper.readValue(response, FreelanceDto.class);

        // Delete the freelance
        mockMvc.perform(delete("/freelances/{id}", createdFreelance.getId()))
                .andExpect(status().isNoContent());

        // Verify it's deleted
        mockMvc.perform(get("/freelances/{id}", createdFreelance.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllFreelances_ShouldReturnFreelancesList() throws Exception {
        // Create a freelance first
        FreelanceDto freelanceDto = createValidFreelanceDto();
        
        mockMvc.perform(post("/freelances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(freelanceDto)))
                .andExpect(status().isCreated());

        // Get all freelances
        mockMvc.perform(get("/freelances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[0].firstName", is("John")))
                .andExpect(jsonPath("$[0].lastName", is("Doe")));
    }

    @Test
    void getFreelanceByEmail_WithExistingEmail_ShouldReturnFreelance() throws Exception {
        // First create a freelance
        FreelanceDto freelanceDto = createValidFreelanceDto();
        
        mockMvc.perform(post("/freelances")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(freelanceDto)))
                .andExpect(status().isCreated());

        // Then retrieve by email
        mockMvc.perform(get("/freelances/by-email")
                .param("email", "john.doe@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("John")))
                .andExpect(jsonPath("$.lastName", is("Doe")))
                .andExpect(jsonPath("$.email", is("john.doe@example.com")));
    }

    @Test
    void getFreelanceByEmail_WithNonExistentEmail_ShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/freelances/by-email")
                .param("email", "nonexistent@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Resource Not Found")));
    }

    private FreelanceDto createValidFreelanceDto() {
        FreelanceDto freelanceDto = new FreelanceDto();
        freelanceDto.setFirstName("John");
        freelanceDto.setLastName("Doe");
        freelanceDto.setEmail("john.doe@example.com");
        freelanceDto.setPhone("123-456-7890");
        freelanceDto.setStatus(EmploymentStatus.FREELANCE);
        return freelanceDto;
    }
}
