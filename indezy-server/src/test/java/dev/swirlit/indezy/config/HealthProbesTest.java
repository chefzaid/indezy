package dev.swirlit.indezy.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:healthProbes;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "indezy.security.permit-all=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthProbesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource database;

    @Test
    @DirtiesContext
    void databaseOutageRemovesReadinessWithoutRestartingTheApi() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());

        database.unwrap(HikariDataSource.class).close();

        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }
}
