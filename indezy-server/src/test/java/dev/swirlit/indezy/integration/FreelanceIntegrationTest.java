package dev.swirlit.indezy.integration;

import tools.jackson.databind.ObjectMapper;
import dev.swirlit.indezy.dto.FreelanceDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import jakarta.annotation.PostConstruct;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Freelance profile endpoints against the database; workspaces come from test-data.sql. */
@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql(scripts = "/test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
    void getAllFreelances_ShouldReturnTheSeededProfiles() throws Exception {
        mockMvc.perform(get("/freelances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].email", hasItem("john.doe@example.com")));
    }

    @Test
    void getFreelance_WithExistingId_ShouldReturnFreelance() throws Exception {
        mockMvc.perform(get("/freelances/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.firstName", is("John")))
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
        String current = mockMvc.perform(get("/freelances/{id}", 2L))
                .andReturn().getResponse().getContentAsString();
        FreelanceDto freelance = objectMapper.readValue(current, FreelanceDto.class);
        freelance.setFirstName("Janet");

        mockMvc.perform(put("/freelances/{id}", 2L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(freelance)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Janet")));
    }

    @Test
    void updateFreelance_WithInvalidData_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(put("/freelances/{id}", 1L)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new FreelanceDto())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.validationErrors").exists());
    }
}
