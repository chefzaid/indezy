package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.realtime.NetworkEvent;
import dev.swirlit.indezy.realtime.NetworkEventHub;
import dev.swirlit.indezy.realtime.NetworkEventRelay;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * What only PostgreSQL runs (see docs/testing.md#postgresql-specifics): a network event goes out as a {@code NOTIFY} and
 * comes back through the relay's own listening connection; the offers' trigram index is built and their long texts kept
 * out of their rows. Run on request against a disposable database, as the load test is: Maven leaves the {@code postgres}
 * tag out otherwise.
 */
@Tag("postgres")
@SpringBootTest(properties = {
    "spring.datasource.url=${INDEZY_LOAD_DATABASE_URL:jdbc:postgresql://127.0.0.1:55432/indezy_load}",
    "spring.datasource.driverClassName=org.postgresql.Driver",
    "spring.datasource.username=${INDEZY_LOAD_DATABASE_USER:indezy}",
    "spring.datasource.password=${INDEZY_LOAD_DATABASE_PASSWORD:indezy}",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
})
@ActiveProfiles("test")
class PostgresSpecificsTest {

    @Autowired
    private NetworkEventRelay relay;

    @MockitoSpyBean
    private NetworkEventHub hub;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void relaysANetworkEventThroughPostgresNotifications() {
        NetworkEvent event = new NetworkEvent(NetworkEvent.MESSAGE, 4L, null);

        relay.send(List.of(7L, 8L), event);

        // Delivered by the listening connection once PostgreSQL notified it, not by the sender's thread.
        verify(hub, timeout(15_000)).deliver(eq(List.of(7L, 8L)), eq(event));
    }

    @Test
    void buildsTheTrigramIndexAndKeepsTheOffersLongTextsOutOfTheirRows() {
        assertThat(jdbcTemplate.queryForObject("SELECT i.indisvalid FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid " +
            "WHERE c.relname = 'idx_job_offers_search_text_trgm'", Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT array_to_string(reloptions, ',') FROM pg_class WHERE relname = 'job_offers'",
            String.class)).contains("toast_tuple_target=256");
        assertThat(Set.of(jdbcTemplate.queryForObject("SELECT extname FROM pg_extension WHERE extname = 'pg_trgm'", String.class)))
            .containsExactly("pg_trgm");
    }
}
