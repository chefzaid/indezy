package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.model.enums.WorkMode;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An interview step's preparation checklist, from the API to the database and back. */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:checklisttestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql(scripts = "/project-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InterviewStepChecklistIntegrationTest {

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
    void keepsTheChecklistInItsOrderAndReplacesItOnUpdate() throws Exception {
        long projectId = createProject();
        String step = """
            {"title": "Technical interview", "status": "PLANNED", "projectId": %d,
             "checklist": [{"label": "  Reread the job description ", "done": true}, {"label": "Test the video call", "done": false}]}
            """.formatted(projectId);

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/interview-steps")
                .contentType(MediaType.APPLICATION_JSON).content(step))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.checklist[*].label", contains("Reread the job description", "Test the video call")))
            .andExpect(jsonPath("$.checklist[*].done", contains(true, false)))
            .andReturn().getResponse().getContentAsString());
        long stepId = created.get("id").asLong();

        String ticked = """
            {"title": "Technical interview", "status": "PLANNED", "projectId": %d,
             "checklist": [{"label": "Test the video call", "done": true}, {"label": "Prepare questions", "done": false},
                           {"label": "Reread the job description", "done": true}]}
            """.formatted(projectId);
        mockMvc.perform(put("/interview-steps/" + stepId).contentType(MediaType.APPLICATION_JSON).content(ticked))
            .andExpect(status().isOk());

        mockMvc.perform(get("/interview-steps/by-project/" + projectId + "/ordered"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].checklist[*].label", contains("Test the video call", "Prepare questions", "Reread the job description")))
            .andExpect(jsonPath("$[0].checklist[*].done", contains(true, false, true)));
    }

    @Test
    void savesAStepWithoutAChecklistAsHavingNone() throws Exception {
        long projectId = createProject();
        String step = """
            {"title": "First contact", "status": "TO_PLAN", "projectId": %d, "checklist": null}
            """.formatted(projectId);

        mockMvc.perform(post("/interview-steps").contentType(MediaType.APPLICATION_JSON).content(step))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.checklist", is(empty())));
    }

    @Test
    void refusesABlankOrTooLongItem() throws Exception {
        long projectId = createProject();
        for (String label : new String[] {"   ", "x".repeat(201)}) {
            String step = """
                {"title": "Call", "status": "TO_PLAN", "projectId": %d, "checklist": [{"label": "%s", "done": false}]}
                """.formatted(projectId, label);

            mockMvc.perform(post("/interview-steps").contentType(MediaType.APPLICATION_JSON).content(step))
                .andExpect(status().isBadRequest());
        }
    }

    private long createProject() throws Exception {
        ProjectDto project = new ProjectDto();
        project.setRole("Backend developer");
        project.setDailyRate(600);
        project.setWorkMode(WorkMode.REMOTE);
        project.setStartDate(LocalDate.now());
        project.setFreelanceId(100L);
        project.setClientId(100L);
        String body = mockMvc.perform(post("/projects").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(project)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }
}
