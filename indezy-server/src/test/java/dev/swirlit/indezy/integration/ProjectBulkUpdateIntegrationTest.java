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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Several opportunities changed at once from the Kanban board or the list: status, source, tag, archive or deletion. */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:bulktestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql(scripts = "/project-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectBulkUpdateIntegrationTest {

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
        jdbc.update("delete from projects where freelance_id = 100");
        for (long id = 1; id <= 3; id++) {
            jdbc.update("""
                insert into projects (id, role, status, lost_reason, daily_rate, is_favorite, freelance_id, client_id, created_at, updated_at, version)
                values (?, ?, 'LOST', 'RATE_TOO_LOW', 600, false, 100, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""", id, "Role " + id);
        }
    }

    @Test
    void movesTheSelectionToAStatusWithWhoRefusedItAndWhyOrWithoutThem() throws Exception {
        bulk("""
            {"projectIds": [1, 2], "action": "STATUS", "status": "OFFER", "refusedBy": "RECRUITER", "lostReason": "OTHER"}""")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(2));
        assertThat(rows("status", "refused_by", "lost_reason")).containsExactly(
            Map.of("STATUS", "OFFER"), Map.of("STATUS", "OFFER"), Map.of("STATUS", "LOST", "LOST_REASON", "RATE_TOO_LOW"));

        bulk("""
            {"projectIds": [1], "action": "STATUS", "status": "LOST", "refusedBy": "RECRUITER", "lostReason": "NO_RESPONSE"}""")
            .andExpect(status().isOk());
        assertThat(rows("status", "refused_by", "lost_reason").get(0))
            .isEqualTo(Map.of("STATUS", "LOST", "REFUSED_BY", "RECRUITER", "LOST_REASON", "NO_RESPONSE"));

        // The reason is optional.
        bulk("""
            {"projectIds": [2], "action": "STATUS", "status": "LOST", "refusedBy": "FREELANCE"}""")
            .andExpect(status().isOk());
        assertThat(rows("status", "refused_by", "lost_reason").get(1)).isEqualTo(Map.of("STATUS", "LOST", "REFUSED_BY", "FREELANCE"));
    }

    @Test
    void assignsASourceAndArchivesTheSelection() throws Exception {
        bulk("""
            {"projectIds": [2, 3], "action": "SOURCE", "sourceId": 100}""").andExpect(status().isOk());
        bulk("""
            {"projectIds": [1, 3], "action": "ARCHIVE"}""").andExpect(status().isOk());

        assertThat(jdbc.queryForList("select source_id from projects order by id", Long.class)).containsExactly(null, 100L, 100L);
        assertThat(jdbc.queryForList("select id from projects where archived_at is not null order by id", Long.class))
            .containsExactly(1L, 3L);
    }

    @Test
    void addsATagOnceToEachTechStackWithRoomForIt() throws Exception {
        jdbc.update("update projects set tech_stack = 'Java, kafka' where id = 1");
        jdbc.update("update projects set tech_stack = ? where id = 3", "x".repeat(250));

        bulk("""
            {"projectIds": [1, 2, 3], "action": "TAG", "tag": " Kafka "}""")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(1));

        assertThat(jdbc.queryForList("select tech_stack from projects order by id", String.class))
            .containsExactly("Java, kafka", "Kafka", "x".repeat(250));

        // Another spelling of a skill the stack names is the same tag.
        jdbc.update("update projects set tech_stack = 'JavaScript' where id = 2");
        bulk("""
            {"projectIds": [2], "action": "TAG", "tag": "JS"}""").andExpect(jsonPath("$.updated").value(0));
    }

    @Test
    void deletesTheSelectionWithWhatHangsOnIt() throws Exception {
        jdbc.update("""
            insert into project_notes (id, project_id, content, created_at, updated_at, version)
            values (900, 1, 'Call back', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""");

        bulk("""
            {"projectIds": [1, 3], "action": "DELETE"}""")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(2));

        assertThat(jdbc.queryForList("select id from projects where freelance_id = 100 order by id", Long.class)).containsExactly(2L);
        assertThat(jdbc.queryForObject("select count(*) from project_notes where project_id = 1", Integer.class)).isZero();
    }

    @Test
    void refusesAnIncompleteChange() throws Exception {
        bulk("""
            {"projectIds": [1], "action": "STATUS"}""").andExpect(status().isBadRequest());
        bulk("""
            {"projectIds": [1], "action": "SOURCE"}""").andExpect(status().isBadRequest());
        bulk("""
            {"projectIds": [], "action": "ARCHIVE"}""").andExpect(status().isBadRequest());
        bulk("""
            {"projectIds": [1, null], "action": "ARCHIVE"}""").andExpect(status().isBadRequest());
        bulk("""
            {"projectIds": [1], "action": "TAG", "tag": "  "}""").andExpect(status().isBadRequest());
        bulk("""
            {"projectIds": [1], "action": "TAG", "tag": "Java, Go"}""").andExpect(status().isBadRequest());
        bulk("{\"projectIds\": [1], \"action\": \"TAG\", \"tag\": \"" + "x".repeat(61) + "\"}").andExpect(status().isBadRequest());
        bulk("""
            {"projectIds": [1], "action": "SOURCE", "sourceId": 999}""").andExpect(status().isNotFound());
    }

    /** Sends the change and writes it: the request joins the test's transaction, read back with plain SQL. */
    private ResultActions bulk(String body) throws Exception {
        ResultActions result =
            mockMvc.perform(patch("/projects/bulk").contentType(MediaType.APPLICATION_JSON).content(body));
        entityManager.flush();
        entityManager.clear();
        return result;
    }

    private List<Map<String, Object>> rows(String... columns) {
        return jdbc.queryForList("select " + String.join(", ", columns) + " from projects order by id").stream()
            .map(row -> {
                Map<String, Object> kept = new HashMap<>();
                row.forEach((key, value) -> {
                    if (value != null) {
                        kept.put(key.toUpperCase(), value);
                    }
                });
                return kept;
            })
            .toList();
    }
}
