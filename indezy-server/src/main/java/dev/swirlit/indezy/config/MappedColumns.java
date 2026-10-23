package dev.swirlit.indezy.config;

import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.SelectableConsumer;

import java.util.Locale;

/** The columns the entities map, as the schema runners compare them with the database. */
final class MappedColumns {

    private MappedColumns() {
    }

    /** Visits every column of the entities: identifiers, attributes, embeddables and element collections. */
    static void forEach(MappingMetamodel metamodel, SelectableConsumer consumer) {
        metamodel.forEachEntityDescriptor(entity -> {
            entity.getIdentifierMapping().forEachSelectable(consumer);
            entity.forEachSelectable(consumer);
        });
        metamodel.forEachCollectionDescriptor(collection -> collection.getAttributeMapping().getElementDescriptor().forEachSelectable(consumer));
    }

    /** A column's {@code table.column}, as the catalog names them. */
    static String key(String table, String column) {
        String name = table.substring(table.lastIndexOf('.') + 1);
        return (name + "." + column).replace("\"", "").toLowerCase(Locale.ROOT);
    }

    /** A quoted identifier. */
    static String identifier(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
