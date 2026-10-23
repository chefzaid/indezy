package dev.swirlit.indezy.config;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.SelectableConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lets an existing PostgreSQL schema store every value of the entities' enums.
 *
 * <p>Hibernate's {@code ddl-auto: update} gives an enum column a check constraint listing the enum's values when it
 * creates the column, but never rewrites it, so a value added later (a job board added to {@code OfferSite}) would be
 * refused on insert. This runner rebuilds each constraint that misses a value of its column's enum, allowing the
 * enum's values and those it allowed before, so no stored row breaks it and the version still running during a
 * rollout keeps writing. It does nothing once every constraint allows its enum, and nothing on another database.
 * {@link ProjectStatusMigration}, which retires statuses, runs first.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class EnumCheckConstraints implements ApplicationRunner {

    /** A quoted value of a constraint's definition, {@code ''} standing for a quote. */
    private static final Pattern QUOTED = Pattern.compile("'((?:[^']|'')*)'");

    /** A single-column check constraint of the schema. */
    record Constraint(String table, String column, String name, String definition) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Supplier<Map<String, List<String>>> enumColumns;

    @Autowired
    public EnumCheckConstraints(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, EntityManagerFactory entityManagerFactory) {
        this(jdbcTemplate, transactionTemplate,
            () -> enumColumns(entityManagerFactory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel()));
    }

    EnumCheckConstraints(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, Supplier<Map<String, List<String>>> enumColumns) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.enumColumns = enumColumns;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!PostgresCatalog.isPostgres(jdbcTemplate)) {
            return;
        }
        Map<String, List<String>> columns = enumColumns.get();
        for (Constraint constraint : constraints()) {
            List<String> values = columns.get(key(constraint.table(), constraint.column()));
            Set<String> allowed = allowed(constraint.definition());
            if (values != null && !allowed.isEmpty() && !allowed.containsAll(values)) {
                widen(constraint, values, allowed);
            }
        }
    }

    /** The enum columns the entities map, as {@code table.column}, with their enum's values in order. */
    static Map<String, List<String>> enumColumns(MappingMetamodel metamodel) {
        Map<String, List<String>> columns = new TreeMap<>();
        SelectableConsumer collect = (index, selectable) -> {
            Class<?> type = selectable.getJdbcMapping().getJavaTypeDescriptor().getJavaTypeClass();
            // Stored by name; an ordinal column's constraint is a range, not a list.
            if (type.isEnum() && !selectable.isFormula() && !selectable.getJdbcMapping().getJdbcType().isNumber()) {
                columns.put(key(selectable.getContainingTableExpression(), selectable.getSelectionExpression()),
                    Arrays.stream(type.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList());
            }
        };
        metamodel.forEachEntityDescriptor(entity -> {
            entity.getIdentifierMapping().forEachSelectable(collect);
            entity.forEachSelectable(collect);
        });
        metamodel.forEachCollectionDescriptor(collection -> collection.getAttributeMapping().getElementDescriptor().forEachSelectable(collect));
        return columns;
    }

    /** The values a check constraint's definition lists ({@code CHECK (site IN ('A', 'B'))} as PostgreSQL prints it). */
    static Set<String> allowed(String definition) {
        Set<String> values = new LinkedHashSet<>();
        Matcher quoted = QUOTED.matcher(definition);
        while (quoted.find()) {
            values.add(quoted.group(1).replace("''", "'"));
        }
        return values;
    }

    private List<Constraint> constraints() {
        return jdbcTemplate.query(
            "SELECT t.relname, a.attname, c.conname, pg_get_constraintdef(c.oid) FROM pg_constraint c " +
                "JOIN pg_class t ON t.oid = c.conrelid JOIN pg_namespace n ON n.oid = t.relnamespace " +
                "JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = c.conkey[1] " +
                "WHERE c.contype = 'c' AND cardinality(c.conkey) = 1 AND n.nspname = current_schema()",
            (row, index) -> new Constraint(row.getString(1), row.getString(2), row.getString(3), row.getString(4)));
    }

    private void widen(Constraint constraint, List<String> values, Set<String> allowed) {
        Set<String> widened = new LinkedHashSet<>(values);
        widened.addAll(allowed);
        String table = identifier(constraint.table());
        String name = identifier(constraint.name());
        String list = widened.stream().map(value -> "'" + value.replace("'", "''") + "'").collect(Collectors.joining(", "));
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("ALTER TABLE " + table + " DROP CONSTRAINT " + name);
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + name + " CHECK (" + identifier(constraint.column()) + " IN (" + list + "))");
        });
        log.info("Check constraint {} now allows {} {} values", constraint.name(), widened.size(), constraint.table() + "." + constraint.column());
    }

    private static String key(String table, String column) {
        String name = table.substring(table.lastIndexOf('.') + 1);
        return (name + "." + column).replace("\"", "").toLowerCase(Locale.ROOT);
    }

    private static String identifier(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
