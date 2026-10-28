package dev.swirlit.indezy.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A reversion rate saved as the share taken becomes the share kept, once. Without a test transaction, as adding the
 * old column commits; the rows and the column go after each test.
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ReversionRateMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM freelances WHERE id IN (1, 2)");
        jdbc.execute("ALTER TABLE freelances DROP COLUMN IF EXISTS reversion_rate");
    }

    @Test
    void doesNothingOnASchemaWithoutTheOldColumn() {
        insert(1L, "ada@example.com");
        jdbc.update("UPDATE freelances SET reversion_share = 55 WHERE id = 1");

        migration().run(null);

        assertThat(jdbc.queryForObject("SELECT reversion_share FROM freelances WHERE id = 1", Double.class)).isEqualTo(55.0);
    }

    @Test
    void movesTheShareTakenToTheShareKeptAndEmptiesTheOldColumn() {
        jdbc.execute("ALTER TABLE freelances ADD COLUMN reversion_rate DOUBLE PRECISION");
        insert(1L, "ada@example.com");
        insert(2L, "bob@example.com");
        jdbc.update("UPDATE freelances SET reversion_rate = 46 WHERE id = 1");

        migration().run(null);
        migration().run(null);

        Map<String, Object> moved = jdbc.queryForMap("SELECT reversion_rate, reversion_share FROM freelances WHERE id = 1");
        assertThat(moved.get("REVERSION_SHARE")).isEqualTo(54.0);
        assertThat(moved.get("REVERSION_RATE")).isNull();
        assertThat(jdbc.queryForObject("SELECT reversion_share FROM freelances WHERE id = 2", Double.class)).isNull();
    }

    private ReversionRateMigration migration() {
        return new ReversionRateMigration(jdbc, new TransactionTemplate(transactionManager));
    }

    private void insert(long id, String email) {
        jdbc.update("INSERT INTO freelances (id, first_name, last_name, email, status, created_at, updated_at, version) " +
            "VALUES (?, 'Ada', 'Lovelace', ?, 'FREELANCE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)", id, email);
    }
}
