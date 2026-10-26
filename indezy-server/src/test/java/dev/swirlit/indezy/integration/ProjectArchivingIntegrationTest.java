package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.ProjectArchivingService;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Opportunities archived after their account's inactivity period, by hand, and restored to their Kanban column. */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:archivingtestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql(scripts = "/project-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectArchivingIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 3, 30);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProjectArchivingService archiving;

    private MockMvc mockMvc;

    @PostConstruct
    void setUpMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @BeforeEach
    void setUp() {
        jdbc.update("delete from projects where freelance_id in (100, 101)");
        jdbc.update("""
            insert into freelances (id, first_name, last_name, email, status, created_at, updated_at, version)
            select 101, 'Other', 'Workspace', 'other@example.com', 'FREELANCE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
            where not exists (select 1 from freelances where id = 101)""");
        account("jane.smith@example.com", true, 10);
        account("other@example.com", false, 10);
    }

    @Test
    void archivesTheIdleOpportunitiesOfTheAccountsThatAskedForIt() {
        project(1, "Idle contact", "CONTACT", 100, 10);
        project(2, "Idle offer", "OFFER", 100, 30);
        project(3, "Recent interview", "INTERVIEW", 100, 9);
        project(4, "Idle but won", "WON", 100, 40);
        project(5, "Idle but lost", "LOST", 100, 40);
        project(6, "Idle elsewhere", "CONTACT", 101, 40);

        assertThat(archiving.archiveIdle(NOW)).isEqualTo(2);

        assertThat(archivedRoles()).containsExactly("Idle contact", "Idle offer");
        // A second run leaves the opportunities it archived as they are.
        assertThat(archiving.archiveIdle(NOW)).isZero();
    }

    @Test
    void usesTheDefaultPeriodForAnAccountWithoutOne() {
        account("jane.smith@example.com", true, null);
        project(1, "Idle for 13 days", "CONTACT", 100, 13);
        project(2, "Idle for 14 days", "CONTACT", 100, 14);

        archiving.archiveIdle(NOW);

        assertThat(archivedRoles()).containsExactly("Idle for 14 days");
    }

    @Test
    void archivesByHandAndRestoresToTheKanbanColumn() throws Exception {
        project(1, "Java developer", "INTERVIEW", 100, 1);

        mockMvc.perform(patch("/projects/1/archive"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt", notNullValue()))
            .andExpect(jsonPath("$.status").value("INTERVIEW"));
        mockMvc.perform(get("/projects/kanban/100"))
            .andExpect(jsonPath("$.columns.INTERVIEW").isEmpty());
        mockMvc.perform(get("/projects/by-freelance/100"))
            .andExpect(jsonPath("$[0].archivedAt", notNullValue()));

        mockMvc.perform(patch("/projects/1/restore"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt", nullValue()));
        mockMvc.perform(get("/projects/kanban/100"))
            .andExpect(jsonPath("$.columns.INTERVIEW[*].role", contains("Java developer")));
        // Restoring is activity: the next night does not archive it again.
        assertThat(archiving.archiveIdle(LocalDateTime.now())).isZero();
    }

    private void account(String email, boolean autoArchive, Integer inactivityDays) {
        User user = users.findByEmail(email).orElseGet(User::new);
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("Account");
        user.setPasswordHash("hash");
        user.setAutoArchive(autoArchive);
        user.setInactivityDays(inactivityDays);
        users.saveAndFlush(user);
    }

    private void project(long id, String role, String status, long freelanceId, int idleDays) {
        Timestamp updated = Timestamp.valueOf(NOW.minusDays(idleDays));
        jdbc.update("""
            insert into projects (id, role, status, daily_rate, is_favorite, freelance_id, client_id, created_at, updated_at, version)
            values (?, ?, ?, 600, false, ?, 100, ?, ?, 0)""", id, role, status, freelanceId, updated, updated);
    }

    private java.util.List<String> archivedRoles() {
        return jdbc.queryForList("select role from projects where archived_at is not null order by id", String.class);
    }
}
