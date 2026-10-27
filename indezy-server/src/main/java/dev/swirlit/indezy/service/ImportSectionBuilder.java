package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.WorkspaceImportReport;
import dev.swirlit.indezy.dto.WorkspaceImportReport.Item;
import dev.swirlit.indezy.dto.WorkspaceImportReport.Kind;
import dev.swirlit.indezy.dto.WorkspaceImportReport.Status;

import java.util.ArrayList;
import java.util.List;

/** Counts one section of an import and lists its first records. */
final class ImportSectionBuilder {

    private final Kind kind;
    private final List<Item> items = new ArrayList<>();
    private int total;
    private int added;
    private int duplicates;
    private int invalid;

    ImportSectionBuilder(Kind kind) {
        this.kind = kind;
    }

    void add(String label) {
        added++;
        record(label, Status.NEW, null);
    }

    void duplicate(String label) {
        duplicates++;
        record(label, Status.DUPLICATE, null);
    }

    void invalid(String label, String reason) {
        invalid++;
        record(label, Status.INVALID, reason);
    }

    WorkspaceImportReport.Section build() {
        return new WorkspaceImportReport.Section(kind, total, added, duplicates, invalid, items);
    }

    private void record(String label, Status status, String reason) {
        total++;
        if (items.size() < WorkspaceImportReport.ITEMS_MAX) {
            items.add(new Item(label == null || label.isBlank() ? "—" : label.strip(), status, reason));
        }
    }
}
