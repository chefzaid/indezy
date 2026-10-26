package dev.swirlit.indezy.integration;

import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The workspace searched from anywhere: opportunities, clients, contacts and notes at once. */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:globalsearchtestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql(scripts = "/project-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GlobalSearchIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbc;

    private MockMvc mockMvc;

    @PostConstruct
    void setUpMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @BeforeEach
    void setUp() {
        jdbc.update("""
            insert into clients (id, company_name, city, is_final, notes, freelance_id, created_at, updated_at, version)
            values (400, 'Société Générale', 'Paris', true, 'Banque', 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""");
        jdbc.update("""
            insert into contacts (id, first_name, last_name, email, client_id, freelance_id, created_at, updated_at, version)
            values (400, 'Hélène', 'Durand', 'helene@sg.example', 400, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""");
        jdbc.update("""
            insert into projects (id, role, status, daily_rate, is_favorite, tech_stack, description, freelance_id, client_id,
                                  created_at, updated_at, version)
            values (400, 'Développeur Kafka', 'CONTACT', 650, false, 'Java, Kafka', 'Flux de paiements temps réel', 100, 400,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""");
        jdbc.update("""
            insert into project_notes (id, project_id, content, created_at, updated_at, version)
            values (400, 400, 'Appel avec Hélène : la mission démarre en janvier, budget validé.', CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, 0)""");
    }

    @Test
    void findsEveryKindOfRecordAccentsAndCaseAside() throws Exception {
        search("helene")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contacts[0].title").value("Hélène Durand"))
            .andExpect(jsonPath("$.contacts[0].subtitle").value("Société Générale"))
            .andExpect(jsonPath("$.contacts[0].excerpt").doesNotExist())
            .andExpect(jsonPath("$.notes[0].title").value("Développeur Kafka"))
            .andExpect(jsonPath("$.notes[0].projectId").value(400))
            .andExpect(jsonPath("$.notes[0].excerpt").value("Appel avec Hélène : la mission démarre en janvier, budget validé."))
            .andExpect(jsonPath("$.projects", hasSize(0)));
        search("SOCIETE generale")
            .andExpect(jsonPath("$.clients[0].title").value("Société Générale"))
            .andExpect(jsonPath("$.clients[0].subtitle").value("Paris"))
            .andExpect(jsonPath("$.projects[0].title").value("Développeur Kafka"))
            .andExpect(jsonPath("$.projects[0].subtitle").value("Société Générale"));
        search("paiements")
            .andExpect(jsonPath("$.projects[0].excerpt").value("Flux de paiements temps réel"));
        search("kafka janvier")
            .andExpect(jsonPath("$.notes", hasSize(0)))
            .andExpect(jsonPath("$.projects", hasSize(0)));
    }

    @Test
    void refusesATooShortOrTooLongSearch() throws Exception {
        search(" a ").andExpect(status().isBadRequest());
        search("x".repeat(101)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/search")).andExpect(status().isBadRequest());
    }

    private ResultActions search(String query) throws Exception {
        return mockMvc.perform(get("/search").param("q", query).param("freelanceId", "100"));
    }
}
