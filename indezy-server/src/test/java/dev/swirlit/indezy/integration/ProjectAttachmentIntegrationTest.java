package dev.swirlit.indezy.integration;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Files kept with an opportunity: attached, listed, read back, refused and deleted. */
@SpringBootTest
@AutoConfigureWebMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:attachmenttestdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
@Transactional
@WithMockUser
@Sql(scripts = "/project-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectAttachmentIntegrationTest {

    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n".getBytes(StandardCharsets.US_ASCII);

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
        jdbc.update("delete from project_attachments");
        jdbc.update("delete from projects where freelance_id = 100");
        jdbc.update("""
            insert into projects (id, role, status, daily_rate, is_favorite, freelance_id, client_id, created_at, updated_at, version)
            values (1, 'Java developer', 'CONTACT', 600, false, 100, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)""");
    }

    @Test
    void keepsAPdfToShowInThePageAndAWordFileToDownload() throws Exception {
        long pdf = upload("../../Job brief.pdf", "application/octet-stream", PDF);
        long docx = upload("Contrat été.docx", "application/zip", new byte[] {'P', 'K', 3, 4, 20, 0});

        mockMvc.perform(get("/projects/1/attachments"))
            .andExpect(jsonPath("$[*].fileName", contains("Contrat été.docx", "Job brief.pdf")))
            .andExpect(jsonPath("$[*].inline", contains(false, true)))
            .andExpect(jsonPath("$[1].contentType").value("application/pdf"))
            .andExpect(jsonPath("$[1].sizeBytes").value(PDF.length));

        mockMvc.perform(get("/projects/attachments/" + pdf + "/content"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(content().bytes(PDF))
            .andExpect(header().string("Content-Disposition", containsString("inline")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"))
            .andExpect(header().string("Cache-Control", containsString("no-store")));
        mockMvc.perform(get("/projects/attachments/" + docx + "/content"))
            .andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(header().string("Content-Disposition", containsString("UTF-8''Contrat%20%C3%A9t%C3%A9.docx")));

        mockMvc.perform(delete("/projects/attachments/" + pdf)).andExpect(status().isNoContent());
        mockMvc.perform(get("/projects/1/attachments")).andExpect(jsonPath("$[*].fileName", contains("Contrat été.docx")));
        mockMvc.perform(get("/projects/attachments/" + pdf + "/content")).andExpect(status().isNotFound());
    }

    @Test
    void refusesWhatItCannotRecogniseAnEmptyFileAndMoreThanTwentyFiles() throws Exception {
        mockMvc.perform(multipart("/projects/1/attachments").file(file("page.pdf", "application/pdf", "<html>".getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", containsString("Only PDF")));
        mockMvc.perform(multipart("/projects/1/attachments").file(file("empty.pdf", "application/pdf", new byte[0])))
            .andExpect(status().isBadRequest());
        for (int i = 0; i < 20; i++) {
            upload("brief-" + i + ".pdf", "application/pdf", PDF);
        }
        mockMvc.perform(multipart("/projects/1/attachments").file(file("one-more.pdf", "application/pdf", PDF)))
            .andExpect(status().isConflict());
        mockMvc.perform(multipart("/projects/999/attachments").file(file("brief.pdf", "application/pdf", PDF)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletesTheFilesWithTheirOpportunity() throws Exception {
        upload("brief.pdf", "application/pdf", PDF);

        mockMvc.perform(delete("/projects/1")).andExpect(status().isNoContent());
        entityManager.flush();

        assertThat(jdbc.queryForObject("select count(*) from project_attachments", Long.class)).isZero();
    }

    private long upload(String name, String declaredType, byte[] content) throws Exception {
        String body = mockMvc.perform(multipart("/projects/1/attachments").file(file(name, declaredType, content)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("id").asLong();
    }

    private static MockMultipartFile file(String name, String type, byte[] content) {
        return new MockMultipartFile("file", name, type, content);
    }
}
