package dev.swirlit.indezy.config;

import dev.swirlit.indezy.model.enums.ProjectStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aligns an existing PostgreSQL schema with the current {@link ProjectStatus} values.
 *
 * <p>Hibernate's {@code ddl-auto: update} never rewrites the check constraint it generated for the
 * status column, so a changed enum would reject new values. This runner migrates the retired
 * {@code IDENTIFIED} and {@code APPLIED} stages to {@code CONTACT} and rebuilds the constraint. It
 * is idempotent: once the constraint matches the enum it does nothing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class ProjectStatusMigration implements ApplicationRunner {

    static final String CONSTRAINT = "projects_status_check";
    static final List<String> RETIRED_STATUSES = List.of("IDENTIFIED", "APPLIED");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!isPostgres() || !projectsTableExists()) {
            return;
        }
        String definition = currentConstraintDefinition();
        if (definition != null && isUpToDate(definition)) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("ALTER TABLE projects DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
            int migrated = jdbcTemplate.update(
                "UPDATE projects SET status = ? WHERE status IN (?, ?)",
                ProjectStatus.CONTACT.name(), RETIRED_STATUSES.get(0), RETIRED_STATUSES.get(1));
            jdbcTemplate.execute("ALTER TABLE projects ADD CONSTRAINT " + CONSTRAINT
                + " CHECK (status IN (" + allowedValues() + "))");
            log.info("Project status constraint rebuilt; {} opportunities moved to {}", migrated, ProjectStatus.CONTACT);
        });
    }

    /** Whether the constraint already allows exactly the current statuses. */
    static boolean isUpToDate(String definition) {
        boolean allowsEveryStatus = Arrays.stream(ProjectStatus.values())
            .allMatch(status -> definition.contains("'" + status.name() + "'"));
        boolean allowsRetired = RETIRED_STATUSES.stream()
            .anyMatch(status -> definition.contains("'" + status + "'"));
        return allowsEveryStatus && !allowsRetired;
    }

    static String allowedValues() {
        return Arrays.stream(ProjectStatus.values())
            .map(status -> "'" + status.name() + "'")
            .collect(Collectors.joining(", "));
    }

    private boolean isPostgres() {
        String product = jdbcTemplate.execute((Connection connection) ->
            connection.getMetaData().getDatabaseProductName());
        return "PostgreSQL".equalsIgnoreCase(product);
    }

    private boolean projectsTableExists() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT to_regclass('projects') IS NOT NULL", Boolean.class));
    }

    private String currentConstraintDefinition() {
        List<String> definitions = jdbcTemplate.queryForList(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conrelid = 'projects'::regclass AND conname = ?",
            String.class, CONSTRAINT);
        return definitions.isEmpty() ? null : definitions.get(0);
    }
}
