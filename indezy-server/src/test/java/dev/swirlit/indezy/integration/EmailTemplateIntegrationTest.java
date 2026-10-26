package dev.swirlit.indezy.integration;

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
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A workspace's email templates: saved, listed by name, changed and deleted, each name once. */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:emailtemplatetestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql(scripts = "/project-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EmailTemplateIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @PostConstruct
    void setUpMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void savesListsChangesAndDeletesTemplates() throws Exception {
        long relaunch = create("""
            {"name": " Relance ", "subject": " Suivi de ma candidature {{role}} ", "body": "Bonjour {{firstName}},\\n\\nJe reviens vers vous.\\n"}""");
        create("""
            {"name": "Availability", "subject": "Available from {{availability}}", "body": "Hello {{firstName}}"}""");

        mockMvc.perform(get("/email-templates").param("freelanceId", "100"))
            .andExpect(jsonPath("$[*].name", contains("Availability", "Relance")))
            .andExpect(jsonPath("$[1].subject").value("Suivi de ma candidature {{role}}"))
            .andExpect(jsonPath("$[1].body").value("Bonjour {{firstName}},\n\nJe reviens vers vous."));

        mockMvc.perform(put("/email-templates/" + relaunch).param("freelanceId", "100").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Relance ESN", "subject": "Suivi", "body": "Bonjour"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Relance ESN"));
        mockMvc.perform(delete("/email-templates/" + relaunch).param("freelanceId", "100")).andExpect(status().isNoContent());
        mockMvc.perform(get("/email-templates").param("freelanceId", "100")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void refusesATakenNameAnIncompleteTemplateAndAnotherWorkspacesOne() throws Exception {
        long relaunch = create("""
            {"name": "Relance", "subject": "Suivi", "body": "Bonjour"}""");
        long other = create("""
            {"name": "Autre", "subject": "Suivi", "body": "Bonjour"}""");

        mockMvc.perform(post("/email-templates").param("freelanceId", "100").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "RELANCE", "subject": "x", "body": "y"}"""))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("already named")));
        mockMvc.perform(put("/email-templates/" + other).param("freelanceId", "100").contentType(MediaType.APPLICATION_JSON).content("""
            {"name": "relance", "subject": "x", "body": "y"}""")).andExpect(status().isConflict());
        // Renaming a template to its own name in another case is no conflict.
        mockMvc.perform(put("/email-templates/" + relaunch).param("freelanceId", "100").contentType(MediaType.APPLICATION_JSON).content("""
            {"name": "RELANCE", "subject": "x", "body": "y"}""")).andExpect(status().isOk());
        mockMvc.perform(post("/email-templates").param("freelanceId", "100").contentType(MediaType.APPLICATION_JSON).content("""
            {"name": "Vide", "subject": " ", "body": "y"}""")).andExpect(status().isBadRequest());
        mockMvc.perform(delete("/email-templates/" + relaunch).param("freelanceId", "999")).andExpect(status().isNotFound());
    }

    private long create(String body) throws Exception {
        String created = mockMvc.perform(post("/email-templates").param("freelanceId", "100")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }
}
