package dev.swirlit.indezy.integration;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A contact's exchanges: recorded, listed newest first with what they say, changed, deleted and gone with it. */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:contactinteractiontestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql(scripts = "/project-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ContactInteractionIntegrationTest {

    private static final String INTERACTIONS = "/contacts/100/interactions";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;

    @PostConstruct
    void setUpMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void recordsExchangesAndReadsTheRelationshipFromThem() throws Exception {
        mockMvc.perform(get(INTERACTIONS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.interactions", hasSize(0)))
            .andExpect(jsonPath("$.lastContactedOn").value(nullValue()))
            .andExpect(jsonPath("$.responseRate").value(nullValue()))
            .andExpect(jsonPath("$.preferredChannel").value(nullValue()));

        create("2026-09-01", "EMAIL", "OUTGOING", "false", "\" Sent my CV \"");
        long call = create("2026-09-10", "PHONE", "OUTGOING", "true", "null");
        create("2026-09-20", "LINKEDIN", "INCOMING", "true", "\"Asked for my rate\"");
        create("2026-09-25", "LINKEDIN", "OUTGOING", "null", "\"   \"");

        mockMvc.perform(get(INTERACTIONS))
            .andExpect(jsonPath("$.interactions[*].occurredOn", contains("2026-09-25", "2026-09-20", "2026-09-10", "2026-09-01")))
            .andExpect(jsonPath("$.interactions[0].summary").value(nullValue()))
            .andExpect(jsonPath("$.interactions[1].replied").value(nullValue()))
            .andExpect(jsonPath("$.interactions[3].summary").value("Sent my CV"))
            .andExpect(jsonPath("$.interactions[3].contactId").value(100))
            .andExpect(jsonPath("$.lastContactedOn").value("2026-09-25"))
            .andExpect(jsonPath("$.asked").value(2))
            .andExpect(jsonPath("$.answered").value(1))
            .andExpect(jsonPath("$.responseRate").value(50))
            .andExpect(jsonPath("$.preferredChannel").value("LINKEDIN"));
        mockMvc.perform(get("/contacts/100")).andExpect(jsonPath("$.lastContactedOn").value("2026-09-25"));
        mockMvc.perform(get("/contacts/by-client/100")).andExpect(jsonPath("$[0].lastContactedOn").value("2026-09-25"));

        mockMvc.perform(put(INTERACTIONS + "/" + call).contentType(MediaType.APPLICATION_JSON).content("""
                {"occurredOn": "2026-09-30", "channel": "MEETING", "direction": "OUTGOING", "replied": true, "summary": "Coffee"}"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.channel").value("MEETING"));
        mockMvc.perform(get(INTERACTIONS))
            .andExpect(jsonPath("$.lastContactedOn").value("2026-09-30"))
            .andExpect(jsonPath("$.preferredChannel").value("MEETING"));

        mockMvc.perform(delete(INTERACTIONS + "/" + call)).andExpect(status().isNoContent());
        mockMvc.perform(get(INTERACTIONS))
            .andExpect(jsonPath("$.interactions", hasSize(3)))
            .andExpect(jsonPath("$.responseRate").value(0));

        mockMvc.perform(delete("/contacts/100")).andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contact_interactions", Integer.class)).isZero();
    }

    @Test
    void refusesAnIncompleteOverlongOrFutureExchangeAndAnotherContactsOne() throws Exception {
        long sent = create("2026-09-01", "EMAIL", "OUTGOING", "true", "null");

        submit("""
            {"occurredOn": "2026-09-01", "direction": "OUTGOING"}""")
            .andExpect(status().isBadRequest());
        submit("{\"occurredOn\": \"2026-09-01\", \"channel\": \"EMAIL\", \"direction\": \"OUTGOING\", \"summary\": \"" +
            "x".repeat(501) + "\"}")
            .andExpect(status().isBadRequest());
        String future = LocalDate.now(ZoneId.of("Europe/Paris")).plusDays(3).toString();
        submit("{\"occurredOn\": \"" + future + "\", \"channel\": \"EMAIL\", \"direction\": \"INCOMING\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.occurredOn").exists());
        submit("{\"occurredOn\": \"2026-09-01\", \"channel\": \"FAX\", \"direction\": \"INCOMING\"}")
            .andExpect(status().isBadRequest());

        mockMvc.perform(put("/contacts/999/interactions/" + sent).contentType(MediaType.APPLICATION_JSON).content("""
                {"occurredOn": "2026-09-01", "channel": "EMAIL", "direction": "INCOMING"}"""))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete(INTERACTIONS + "/999999")).andExpect(status().isNotFound());
    }

    @Test
    void keepsNoAnswerOnAnExchangeTheContactStarted() throws Exception {
        long received = create("2026-09-01", "EMAIL", "INCOMING", "false", "null");

        mockMvc.perform(get(INTERACTIONS))
            .andExpect(jsonPath("$.interactions[0].id").value(received))
            .andExpect(jsonPath("$.interactions[0].replied").value(nullValue()))
            .andExpect(jsonPath("$.asked").value(0))
            .andExpect(jsonPath("$.preferredChannel").value("EMAIL"));
    }

    @Test
    void remindsToFollowUpAMessageLeftUnanswered() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        create(today.minusDays(9).toString(), "LINKEDIN", "OUTGOING", "null", "\"Sent my CV\"");

        mockMvc.perform(get("/projects/stats/dashboard/100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.followUpsDue[0].contactId").value(100))
            .andExpect(jsonPath("$.followUpsDue[0].name").value("Bob Wilson"))
            .andExpect(jsonPath("$.followUpsDue[0].channel").value("LINKEDIN"))
            .andExpect(jsonPath("$.followUpsDue[0].daysWaiting").value(9));

        create(today.minusDays(1).toString(), "LINKEDIN", "INCOMING", "null", "null");
        mockMvc.perform(get("/projects/stats/dashboard/100")).andExpect(jsonPath("$.followUpsDue", hasSize(0)));
    }

    private long create(String day, String channel, String direction, String replied, String summary) throws Exception {
        String body = submit("{\"occurredOn\": \"" + day + "\", \"channel\": \"" + channel + "\", \"direction\": \"" + direction +
            "\", \"replied\": " + replied + ", \"summary\": " + summary + "}")
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private ResultActions submit(String body) throws Exception {
        return mockMvc.perform(post(INTERACTIONS).contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
