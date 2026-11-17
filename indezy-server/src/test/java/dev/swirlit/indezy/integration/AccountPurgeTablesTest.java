package dev.swirlit.indezy.integration;

import dev.swirlit.indezy.service.account.AccountPurge;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Every table of the schema is either purged with an account or named as holding no account's data. */
class AccountPurgeTablesTest extends TwoAccountsIntegrationTest {

    @Test
    void namesEveryTableOfTheSchema() {
        Set<String> schema = new HashSet<>(jdbcTemplate.queryForList("SELECT LOWER(TABLE_NAME) FROM INFORMATION_SCHEMA.TABLES " +
            "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'", String.class));
        Set<String> named = new HashSet<>(AccountPurge.purgedTables());
        named.addAll(AccountPurge.sharedTables());
        assertThat(named).as("tables named in AccountPurge but missing from the schema").isSubsetOf(schema);
        assertThat(schema).as("tables AccountPurge does not name: purge them or call them shared").isSubsetOf(named);
    }
}
