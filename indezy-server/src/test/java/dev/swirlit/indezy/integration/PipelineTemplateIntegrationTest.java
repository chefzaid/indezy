package dev.swirlit.indezy.integration;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A recruitment process saved from an opportunity's steps, applied to another one, and deleted. */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:templatetestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql(scripts = "/project-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PipelineTemplateIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @PostConstruct
    void setUpMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @BeforeEach
    void setUp() {
        jdbc.update("delete from pipeline_template_steps");
        jdbc.update("delete from pipeline_templates");
        jdbc.update("delete from interview_step_checklist");
        jdbc.update("delete from interview_steps");
        jdbc.update("delete from projects where freelance_id = 100");
        for (long id = 1; id <= 2; id++) {
            jdbc.update("""
                insert into projects (id, role, status, daily_rate, is_favorite, freelance_id, client_id, created_at, updated_at, version)
                values (?, 'Java developer', 'INTERVIEW', 600, false, 100, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""", id);
        }
        step(10, "Technical test", LocalDateTime.of(2026, 10, 2, 10, 0));
        step(11, "Commercial interview", LocalDateTime.of(2026, 9, 28, 14, 0));
        step(12, "Client interview", null);
        jdbc.update("insert into interview_step_checklist (step_id, position, label, done) values (10, 0, 'Revise Spring', true), (10, 1, 'Test the IDE share', false)");
    }

    @Test
    void savesTheStepsInTheirTimelineOrderAndAppliesThemToAnotherOpportunity() throws Exception {
        String created = mockMvc.perform(post("/pipeline-templates").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "  Acme process ", "projectId": 1, "clientId": 100}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Acme process"))
            .andExpect(jsonPath("$.clientId").value(100))
            .andExpect(jsonPath("$.steps[*].title", contains("Commercial interview", "Technical test", "Client interview")))
            .andExpect(jsonPath("$.steps[1].checklist", contains("Revise Spring", "Test the IDE share")))
            .andExpect(jsonPath("$.steps[0].checklist", hasSize(0)))
            .andReturn().getResponse().getContentAsString();
        long templateId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(post("/pipeline-templates/" + templateId + "/apply/2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].title", contains("Commercial interview", "Technical test", "Client interview")))
            .andExpect(jsonPath("$[*].status", contains("TO_PLAN", "TO_PLAN", "TO_PLAN")))
            .andExpect(jsonPath("$[1].checklist[*].label", contains("Revise Spring", "Test the IDE share")))
            .andExpect(jsonPath("$[1].checklist[*].done", contains(false, false)))
            .andExpect(jsonPath("$[0].date", nullValue()));

        mockMvc.perform(get("/pipeline-templates").param("freelanceId", "100"))
            .andExpect(jsonPath("$[*].name", contains("Acme process")))
            .andExpect(jsonPath("$[0].clientName").value("Project Test Corp"));
        mockMvc.perform(delete("/pipeline-templates/" + templateId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/pipeline-templates").param("freelanceId", "100")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void refusesATakenNameAnOpportunityWithoutStepsAndAnUnknownTemplate() throws Exception {
        mockMvc.perform(post("/pipeline-templates").contentType(MediaType.APPLICATION_JSON).content("""
            {"name": "Usual", "projectId": 1}""")).andExpect(status().isCreated());

        mockMvc.perform(post("/pipeline-templates").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "USUAL", "projectId": 1}"""))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("already named")));
        mockMvc.perform(post("/pipeline-templates").contentType(MediaType.APPLICATION_JSON).content("""
            {"name": "Empty", "projectId": 2}""")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/pipeline-templates").contentType(MediaType.APPLICATION_JSON).content("""
            {"name": " ", "projectId": 1}""")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/pipeline-templates/999/apply/1")).andExpect(status().isNotFound());
    }

    @Test
    void savesAStarterProcessWrittenOutStepByStep() throws Exception {
        mockMvc.perform(post("/pipeline-templates/steps").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": " Through an ESN ", "freelanceId": 100, "steps": [
                  {"title": " Call with the ESN ", "checklist": ["Rate\\n  asked", "Availability"]},
                  {"title": "Client interview"}]}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Through an ESN"))
            .andExpect(jsonPath("$.clientId").value(nullValue()))
            .andExpect(jsonPath("$.steps[0].title").value("Call with the ESN"))
            .andExpect(jsonPath("$.steps[0].checklist", contains("Rate asked", "Availability")))
            .andExpect(jsonPath("$.steps[1].checklist", hasSize(0)));

        mockMvc.perform(post("/pipeline-templates/steps").contentType(MediaType.APPLICATION_JSON).content("""
            {"name": "through an esn", "freelanceId": 100, "steps": [{"title": "Call"}]}""")).andExpect(status().isConflict());
        mockMvc.perform(post("/pipeline-templates/steps").contentType(MediaType.APPLICATION_JSON).content("""
            {"name": "No steps", "freelanceId": 100, "steps": []}""")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/pipeline-templates/steps").contentType(MediaType.APPLICATION_JSON).content("""
            {"name": "Blank step", "freelanceId": 100, "steps": [{"title": " "}]}""")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/pipeline-templates/steps").contentType(MediaType.APPLICATION_JSON).content("""
            {"name": "Missing step", "freelanceId": 100, "steps": [null]}""")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/pipeline-templates/steps").contentType(MediaType.APPLICATION_JSON).content(
                objectMapper.writeValueAsString(java.util.Map.of("name", "Too long", "freelanceId", 100,
                    "steps", java.util.Collections.nCopies(21, java.util.Map.of("title", "Call"))))))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/pipeline-templates").param("freelanceId", "100")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void keepsATemplateForAnyoneWhenItsClientIsDeleted() throws Exception {
        jdbc.update("""
            insert into clients (id, company_name, city, is_final, freelance_id, created_at, updated_at, version)
            values (101, 'Short-lived ESN', 'Lyon', false, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""");
        mockMvc.perform(post("/pipeline-templates").contentType(MediaType.APPLICATION_JSON).content("""
            {"name": "ESN process", "projectId": 1, "clientId": 101}""")).andExpect(status().isCreated());

        mockMvc.perform(delete("/clients/101")).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/pipeline-templates").param("freelanceId", "100"))
            .andExpect(jsonPath("$[0].name").value("ESN process"))
            .andExpect(jsonPath("$[0].clientId", nullValue()));
    }

    private void step(long id, String title, LocalDateTime date) {
        jdbc.update("""
            insert into interview_steps (id, title, date, status, project_id, created_at, updated_at, version)
            values (?, ?, ?, 'VALIDATED', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""",
            id, title, date == null ? null : Timestamp.valueOf(date));
    }
}
