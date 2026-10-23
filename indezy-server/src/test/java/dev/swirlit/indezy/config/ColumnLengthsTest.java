package dev.swirlit.indezy.config;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A column an entity made longer accepts longer values in a schema created before, and none is ever narrowed. */
@DataJpaTest
@ActiveProfiles("test")
class ColumnLengthsTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    @Test
    void readsTheLengthsTheEntitiesDeclare() {
        Map<String, Long> lengths = ColumnLengths.declaredLengths(entityManagerFactory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel());

        assertThat(lengths).containsEntry("geo_places.region_code", 3L).containsEntry("job_offers.region_code", 3L)
            .containsEntry("job_offers.department_code", 3L).containsEntry("saved_offer_searches.email_token", 43L)
            // An element collection's column.
            .containsEntry("job_offer_skills.skill", 60L)
            // Not text; a TEXT column (the description) keeps a nominal length the database query leaves out.
            .doesNotContainKeys("job_offers.rate_min", "job_offers.published_at");
    }

    @Test
    @SuppressWarnings("unchecked")
    void widensAShorterColumnOnlyAndNeverNarrowsOne() {
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("PostgreSQL");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(
            new ColumnLengths.Column("geo_places", "region_code", 2),
            new ColumnLengths.Column("job_offers", "department_code", 3),
            new ColumnLengths.Column("job_offers", "city", 200),
            new ColumnLengths.Column("legacy_table", "code", 2)));

        new ColumnLengths(jdbc, () -> Map.of("geo_places.region_code", 3L, "job_offers.department_code", 3L, "job_offers.city", 120L)).run(null);

        verify(jdbc).execute("ALTER TABLE \"geo_places\" ALTER COLUMN \"region_code\" TYPE varchar(3)");
        verify(jdbc, never()).execute(contains("department_code"));
        verify(jdbc, never()).execute(contains("city"));
        verify(jdbc, never()).execute(contains("legacy_table"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void changesNothingOnAnotherDatabase() {
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("H2");

        new ColumnLengths(jdbc, Map::of).run(null);

        verify(jdbc, never()).query(anyString(), any(RowMapper.class));
    }
}
