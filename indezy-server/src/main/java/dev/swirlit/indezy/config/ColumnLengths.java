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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Lets an existing PostgreSQL schema hold values as long as the entities allow.
 *
 * <p>Hibernate's {@code ddl-auto: update} creates a varchar column with its length but never widens it, so a column an
 * entity made longer (the region code of an overseas collectivity, "978") keeps refusing longer values. This runner
 * widens each varchar column shorter than its entity declares, which PostgreSQL does in its catalog only, without
 * rewriting the table. It never narrows a column, and does nothing on another database.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class ColumnLengths implements ApplicationRunner {

    /** A varchar column of the schema and its length. */
    record Column(String table, String column, int length) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final Supplier<Map<String, Long>> declaredLengths;

    @Autowired
    public ColumnLengths(JdbcTemplate jdbcTemplate, EntityManagerFactory entityManagerFactory) {
        this(jdbcTemplate, () -> declaredLengths(entityManagerFactory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel()));
    }

    ColumnLengths(JdbcTemplate jdbcTemplate, Supplier<Map<String, Long>> declaredLengths) {
        this.jdbcTemplate = jdbcTemplate;
        this.declaredLengths = declaredLengths;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!PostgresCatalog.isPostgres(jdbcTemplate)) {
            return;
        }
        Map<String, Long> declared = declaredLengths.get();
        for (Column column : varcharColumns()) {
            Long length = declared.get(MappedColumns.key(column.table(), column.column()));
            if (length != null && length > column.length()) {
                jdbcTemplate.execute("ALTER TABLE " + MappedColumns.identifier(column.table()) + " ALTER COLUMN " +
                    MappedColumns.identifier(column.column()) + " TYPE varchar(" + length + ")");
                log.info("Column {}.{} widened from {} to {} characters", column.table(), column.column(), column.length(), length);
            }
        }
    }

    /** The text columns the entities map with a length, as {@code table.column}. */
    static Map<String, Long> declaredLengths(MappingMetamodel metamodel) {
        Map<String, Long> lengths = new TreeMap<>();
        SelectableConsumer collect = (index, selectable) -> {
            if (!selectable.isFormula() && selectable.getLength() != null && selectable.getJdbcMapping().getJdbcType().isStringLikeExcludingClob()) {
                lengths.put(MappedColumns.key(selectable.getContainingTableExpression(), selectable.getSelectionExpression()), selectable.getLength());
            }
        };
        MappedColumns.forEach(metamodel, collect);
        return lengths;
    }

    private List<Column> varcharColumns() {
        return jdbcTemplate.query(
            "SELECT table_name, column_name, character_maximum_length FROM information_schema.columns " +
                "WHERE table_schema = current_schema() AND data_type = 'character varying' AND character_maximum_length IS NOT NULL",
            (row, index) -> new Column(row.getString(1), row.getString(2), row.getInt(3)));
    }
}
