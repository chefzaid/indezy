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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Clients and contacts picked in their lists and deleted at once; a client a mission depends on is kept. */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:bulkdeletetestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql(scripts = "/project-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BulkDeleteIntegrationTest {

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
        for (long id = 200; id <= 202; id++) {
            jdbc.update("""
                insert into clients (id, company_name, city, is_final, freelance_id, created_at, updated_at, version)
                values (?, ?, 'Paris', true, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""", id, "Client " + id);
            jdbc.update("""
                insert into contacts (id, first_name, client_id, freelance_id, created_at, updated_at, version)
                values (?, ?, ?, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""", id, "Contact " + id, id);
        }
        jdbc.update("""
            insert into projects (id, role, status, daily_rate, is_favorite, freelance_id, client_id, created_at, updated_at, version)
            values (900, 'Mission', 'WON', 600, false, 100, 202, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""");
        jdbc.update("""
            insert into contact_interactions (id, contact_id, occurred_on, channel, direction, created_at, updated_at, version)
            values (900, 201, DATE '2026-09-01', 'EMAIL', 'INCOMING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""");
    }

    @Test
    void deletesTheClientsNoMissionDependsOnWithTheirContacts() throws Exception {
        delete("/clients/bulk-delete", "[200, 201, 202]")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(2))
            .andExpect(jsonPath("$.kept").value(1));

        assertThat(jdbc.queryForList("select id from clients where id >= 200", Long.class)).containsExactly(202L);
        assertThat(jdbc.queryForList("select id from contacts where id >= 200", Long.class)).containsExactly(202L);
        assertThat(jdbc.queryForObject("select count(*) from contact_interactions", Integer.class)).isZero();
    }

    @Test
    void deletesTheContactsWithTheirExchanges() throws Exception {
        delete("/contacts/bulk-delete", "[200, 201]")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(2))
            .andExpect(jsonPath("$.kept").value(0));

        assertThat(jdbc.queryForList("select id from contacts where id >= 200", Long.class)).containsExactly(202L);
        assertThat(jdbc.queryForObject("select count(*) from contact_interactions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from clients where id >= 200", Integer.class)).isEqualTo(3);
    }

    @Test
    void refusesAnEmptyOrOversizedSelection() throws Exception {
        delete("/contacts/bulk-delete", "[]").andExpect(status().isBadRequest());
        delete("/clients/bulk-delete", "[200, null]").andExpect(status().isBadRequest());
        delete("/clients/bulk-delete", java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList().toString())
            .andExpect(status().isBadRequest());
    }

    /** Sends the deletion and writes it: the request joins the test's transaction, read back with plain SQL. */
    private ResultActions delete(String path, String ids) throws Exception {
        ResultActions result = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"ids\": " + ids + "}"));
        entityManager.flush();
        entityManager.clear();
        return result;
    }
}
