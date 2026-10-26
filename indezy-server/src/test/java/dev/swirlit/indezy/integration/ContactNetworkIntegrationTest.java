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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who reports to whom and who works with whom among contacts, for the org charts. */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:contactnetworktestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql(scripts = "/project-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ContactNetworkIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private MockMvc mockMvc;

    @PostConstruct
    void setUpMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @BeforeEach
    void setUp() {
        // Contact 100 (Bob Wilson) works at client 100; an ESN (client 300) has Nadia (300) and her manager Paul (301).
        jdbc.update("""
            insert into clients (id, company_name, city, is_final, freelance_id, created_at, updated_at, version)
            values (300, 'Esn Conseil', 'Paris', false, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""");
        jdbc.update("""
            insert into contacts (id, first_name, last_name, client_id, freelance_id, created_at, updated_at, version)
            values (300, 'Nadia', 'Kaci', 300, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
                   (301, 'Paul', 'Roy', 300, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""");
    }

    @Test
    void setsWhomAContactReportsToAndRefusesALoop() throws Exception {
        update(300, "{\"firstName\": \"Nadia\", \"lastName\": \"Kaci\", \"clientId\": 300, \"managerId\": 301}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.managerId").value(301))
            .andExpect(jsonPath("$.managerName").value("Paul Roy"));
        mockMvc.perform(get("/contacts/by-client/300")).andExpect(jsonPath("$[?(@.id == 300)].managerName").value("Paul Roy"));

        update(301, "{\"firstName\": \"Paul\", \"clientId\": 300, \"managerId\": 300}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.managerId").value("This contact already manages, directly or not, the chosen manager"));
        update(301, "{\"firstName\": \"Paul\", \"clientId\": 300, \"managerId\": 301}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.managerId").exists());
        update(301, "{\"firstName\": \"Paul\", \"clientId\": 300, \"managerId\": 999}").andExpect(status().isNotFound());

        mockMvc.perform(post("/contacts").contentType(MediaType.APPLICATION_JSON).content("""
                {"firstName": "Lea", "clientId": 300, "freelanceId": 100, "managerId": 300}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.managerName").value("Nadia Kaci"));

        // The manager leaves: her team stays, reporting to nobody.
        mockMvc.perform(delete("/contacts/301")).andExpect(status().isNoContent());
        entityManager.flush();
        entityManager.clear();
        assertThat(jdbc.queryForObject("select manager_id from contacts where id = 300", Long.class)).isNull();
        update(300, "{\"firstName\": \"Nadia\", \"clientId\": 300}").andExpect(jsonPath("$.managerId").doesNotExist());
    }

    @Test
    void linksContactsWhoWorkTogetherBothWays() throws Exception {
        link(300, "{\"otherId\": 100, \"note\": \" Account manager for the mission \"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.otherName").value("Bob Wilson"))
            .andExpect(jsonPath("$.otherClientName").value("Project Test Corp"))
            .andExpect(jsonPath("$.note").value("Account manager for the mission"));
        link(100, "{\"otherId\": 301}").andExpect(status().isOk());
        link(100, "{\"otherId\": 300, \"note\": \"  \"}").andExpect(jsonPath("$.note").doesNotExist());

        mockMvc.perform(get("/contacts/100/relations"))
            .andExpect(jsonPath("$[*].otherName", contains("Nadia Kaci", "Paul Roy")))
            .andExpect(jsonPath("$[0].otherClientName").value("Esn Conseil"))
            .andExpect(jsonPath("$[0].otherClientIsFinal").value(false));
        mockMvc.perform(get("/clients/300/contact-relations"))
            .andExpect(jsonPath("$[*].contactId", contains(300, 301)))
            .andExpect(jsonPath("$[*].otherId", contains(100, 100)));
        mockMvc.perform(get("/clients/100/contact-relations")).andExpect(jsonPath("$", hasSize(2)));
        assertThat(jdbc.queryForObject("select count(*) from contact_relations", Integer.class)).isEqualTo(2);

        mockMvc.perform(delete("/contacts/300/relations/100")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/contacts/300/relations/100")).andExpect(status().isNotFound());
        mockMvc.perform(delete("/contacts/301")).andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbc.queryForObject("select count(*) from contact_relations", Integer.class)).isZero();
    }

    @Test
    void refusesALinkToOneselfOrAnUnknownContact() throws Exception {
        link(300, "{\"otherId\": 300}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.otherId").exists());
        link(300, "{}").andExpect(status().isBadRequest());
        link(300, "{\"otherId\": 100, \"note\": \"" + "x".repeat(101) + "\"}").andExpect(status().isBadRequest());
        link(300, "{\"otherId\": 999}").andExpect(status().isNotFound());
        mockMvc.perform(get("/clients/999/contact-relations")).andExpect(jsonPath("$", hasSize(0)));
    }

    private ResultActions update(long id, String body) throws Exception {
        return mockMvc.perform(put("/contacts/" + id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions link(long id, String body) throws Exception {
        return mockMvc.perform(put("/contacts/" + id + "/relations").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
