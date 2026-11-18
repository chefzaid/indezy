package dev.swirlit.indezy.config;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A job board removed from the feed leaves no row Hibernate could no longer load. */
@DataJpaTest
@ActiveProfiles("test")
class RetiredOfferSitesTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);

    @Test
    void findsEveryColumnThatStoresASite() {
        List<String> columns = RetiredOfferSites.siteColumns(EnumCheckConstraints.enumColumns(
            entityManagerFactory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel()));

        assertThat(columns).contains("job_offers.site", "offer_appearances.site", "offer_board_visits.site",
            "offer_site_exclusions.site", "offer_site_statuses.site", "offer_skips.site");
        assertThat(columns).doesNotContain("projects.status", "job_offer_states.applied_via");
    }

    @Test
    void deletesTheRowsOfRetiredBoardsWithWhatRefersToTheirOffers() {
        postgres();
        when(jdbc.queryForList(RetiredOfferSites.REFERRERS))
            .thenReturn(List.of(Map.of("referrer", "job_offer_states", "column_name", "offer_id")));
        when(jdbc.update(contains("FROM \"offer_skips\""))).thenReturn(11);
        when(jdbc.update(contains("FROM \"job_offers\" WHERE"))).thenReturn(1);

        runner(List.of("job_offers.site", "offer_skips.site")).run(null);

        String retired = RetiredOfferSites.retired("site");
        assertThat(retired).startsWith("\"site\" NOT IN ('FREE_WORK', 'COLLECTIVE'").endsWith("'KICKLOX')");
        var order = inOrder(jdbc);
        order.verify(jdbc).update("DELETE FROM job_offer_states WHERE \"offer_id\" IN (SELECT id FROM \"job_offers\" WHERE " + retired + ")");
        order.verify(jdbc).update("DELETE FROM \"job_offers\" WHERE " + retired);
        order.verify(jdbc).update("DELETE FROM \"offer_skips\" WHERE " + retired);
    }

    @Test
    void skipsATableNotCreatedYet() {
        postgres();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq("offer_skips"))).thenReturn(false);

        runner(List.of("offer_skips.site")).run(null);

        verify(jdbc, never()).update(anyString());
    }

    @Test
    void changesNothingOnAnotherDatabase() {
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("H2");

        runner(List.of("job_offers.site")).run(null);

        verify(jdbc, never()).update(anyString());
        verify(transactions, never()).executeWithoutResult(any());
    }

    private RetiredOfferSites runner(List<String> columns) {
        return new RetiredOfferSites(jdbc, transactions, () -> columns);
    }

    @SuppressWarnings("unchecked")
    private void postgres() {
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("PostgreSQL");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), anyString())).thenReturn(true);
        doAnswer(invocation -> {
            invocation.<Consumer<Object>>getArgument(0).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
    }
}
