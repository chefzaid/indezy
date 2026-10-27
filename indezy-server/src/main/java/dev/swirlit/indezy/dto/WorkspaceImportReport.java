package dev.swirlit.indezy.dto;

import java.util.List;

/**
 * What an import of an account export adds to a workspace, section by section: each record new, already there (a
 * duplicate, left as it is) or invalid (left out, with why). A preview reports it without writing anything.
 */
public record WorkspaceImportReport(boolean applied, List<Section> sections) {

    /** Records listed per section; the counts cover them all. */
    public static final int ITEMS_MAX = 200;

    public WorkspaceImportReport {
        sections = List.copyOf(sections);
    }

    public enum Kind { SOURCES, CLIENTS, CONTACTS, SEASONS, PROJECTS, PROJECT_NOTES }

    public enum Status { NEW, DUPLICATE, INVALID }

    public record Section(Kind kind, int total, int added, int duplicates, int invalid, List<Item> items) {

        public Section {
            items = List.copyOf(items);
        }
    }

    /** One record, named as the workspace shows it; {@code reason} says why an invalid one is left out. */
    public record Item(String label, Status status, String reason) {

        // Spring MVC logs bodies through toString at DEBUG: the status only, never a client's or contact's name.
        @Override
        public String toString() {
            return "Item[status=" + status + "]";
        }
    }
}
