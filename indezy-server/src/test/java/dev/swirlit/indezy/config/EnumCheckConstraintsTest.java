package dev.swirlit.indezy.config;

import dev.swirlit.indezy.model.enums.OfferSite;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A value added to an enum is accepted by the column a schema created before it, without narrowing what it allowed. */
@DataJpaTest
@ActiveProfiles("test")
class EnumCheckConstraintsTest {

    private static final String SITES = "CHECK (((site)::text = ANY ((ARRAY['FREE_WORK'::character varying, " +
        "'COLLECTIVE'::character varying, 'RETIRED_BOARD'::character varying])::text[])))";

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);

    @Test
    void findsTheEnumColumnsOfEntitiesEmbeddablesAndCollections() {
        Map<String, List<String>> columns = EnumCheckConstraints.enumColumns(
            entityManagerFactory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel());

        assertThat(columns.get("job_offers.site")).isEqualTo(Arrays.stream(OfferSite.values()).map(Enum::name).toList());
        assertThat(columns).containsKeys("offer_site_statuses.site", "offer_skips.site", "offer_site_exclusions.site", "projects.status",
            // An embeddable's column, and an element collection's.
            "job_offer_states.applied_via", "activity_report_days.day_type");
        assertThat(columns).doesNotContainKeys("job_offers.title", "job_offers.rate_min", "projects.personal_rating");
    }

    @Test
    void readsTheValuesOfAConstraintAsPostgresPrintsIt() {
        assertThat(EnumCheckConstraints.allowed(SITES)).containsExactly("FREE_WORK", "COLLECTIVE", "RETIRED_BOARD");
        assertThat(EnumCheckConstraints.allowed("CHECK ((name)::text = ANY (ARRAY['L''Atelier'::text]))")).containsExactly("L'Atelier");
        assertThat(EnumCheckConstraints.allowed("CHECK ((personal_rating >= 1) AND (personal_rating <= 5))")).isEmpty();
    }

    @Test
    void widensAConstraintMissingAValueAndKeepsTheValuesItAllowed() {
        postgres(new EnumCheckConstraints.Constraint("job_offers", "site", "job_offers_site_check", SITES),
            new EnumCheckConstraints.Constraint("projects", "personal_rating", "projects_personal_rating_check",
                "CHECK ((personal_rating >= 1) AND (personal_rating <= 5))"));

        runner(Map.of("job_offers.site", List.of("FREE_WORK", "COLLECTIVE", "OUIDESK"))).run(null);

        var order = inOrder(jdbc);
        order.verify(jdbc).execute("ALTER TABLE \"job_offers\" DROP CONSTRAINT \"job_offers_site_check\"");
        order.verify(jdbc).execute("ALTER TABLE \"job_offers\" ADD CONSTRAINT \"job_offers_site_check\" " +
            "CHECK (\"site\" IN ('FREE_WORK', 'COLLECTIVE', 'OUIDESK', 'RETIRED_BOARD'))");
        verify(jdbc, never()).execute(contains("personal_rating"));
    }

    @Test
    void leavesAConstraintAllowingEveryValueAlone() {
        postgres(new EnumCheckConstraints.Constraint("job_offers", "site", "job_offers_site_check", SITES));

        runner(Map.of("job_offers.site", List.of("FREE_WORK", "COLLECTIVE"))).run(null);

        verify(jdbc, never()).execute(anyString());
    }

    @Test
    void changesNothingOnAnotherDatabase() {
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("H2");

        runner(Map.of()).run(null);

        verify(jdbc, never()).query(anyString(), any(RowMapper.class));
        verify(jdbc, never()).execute(anyString());
    }

    private EnumCheckConstraints runner(Map<String, List<String>> columns) {
        return new EnumCheckConstraints(jdbc, transactions, () -> columns);
    }

    @SuppressWarnings("unchecked")
    private void postgres(EnumCheckConstraints.Constraint... constraints) {
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("PostgreSQL");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(constraints));
        doAnswer(invocation -> {
            invocation.<Consumer<Object>>getArgument(0).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
    }
}
