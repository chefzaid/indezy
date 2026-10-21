package dev.swirlit.indezy.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferSearchIndexTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OfferSearchIndex index = new OfferSearchIndex(jdbc);

    @BeforeEach
    void setUp() {
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("PostgreSQL");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("job_offers"))).thenReturn(true);
    }

    @Test
    void buildsTheIndexWithTheExtensionWhenMissing() {
        validity();

        index.run(null);

        var order = inOrder(jdbc);
        order.verify(jdbc).execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
        order.verify(jdbc).execute("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_job_offers_search_text_trgm ON job_offers " +
            "USING gin (search_text gin_trgm_ops)");
        verify(jdbc, never()).execute(startsWith("DROP"));
    }

    @Test
    void buildsAgainAnIndexAnInterruptedBuildLeftInvalid() {
        validity(false);

        index.run(null);

        var order = inOrder(jdbc);
        order.verify(jdbc).execute("DROP INDEX CONCURRENTLY IF EXISTS idx_job_offers_search_text_trgm");
        order.verify(jdbc).execute(startsWith("CREATE INDEX CONCURRENTLY"));
    }

    @Test
    void leavesAValidIndexAlone() {
        validity(true);

        index.run(null);

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void keepsSearchingWithoutTheIndexWhenTheExtensionIsRefused() {
        validity();
        doThrow(new DataAccessResourceFailureException("permission denied to create extension \"pg_trgm\""))
            .when(jdbc).execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");

        index.run(null);

        verify(jdbc, never()).execute(contains("CREATE INDEX"));
    }

    @Test
    void changesNothingOnAnotherDatabaseOrBeforeTheTableExists() {
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("H2");
        index.run(null);
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("PostgreSQL");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("job_offers"))).thenReturn(false);
        index.run(null);

        verify(jdbc, never()).queryForList(anyString(), eq(Boolean.class), eq(OfferSearchIndex.INDEX));
        verify(jdbc, never()).execute(anyString());
    }

    private void validity(Boolean... valid) {
        when(jdbc.queryForList(anyString(), eq(Boolean.class), eq(OfferSearchIndex.INDEX))).thenReturn(List.of(valid));
    }
}
