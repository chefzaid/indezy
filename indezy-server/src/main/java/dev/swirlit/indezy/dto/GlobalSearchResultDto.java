package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** What the global search found in a workspace, by kind, each list at most a few hits, best first. */
public record GlobalSearchResultDto(List<Hit> projects, List<Hit> clients, List<Hit> contacts, List<Hit> notes) {

    public GlobalSearchResultDto {
        projects = List.copyOf(projects);
        clients = List.copyOf(clients);
        contacts = List.copyOf(contacts);
        notes = List.copyOf(notes);
    }

    /**
     * One record found: its title (an opportunity's role, a client's or contact's name, a note's opportunity), what places
     * it (a client, a city), and, when the words were found elsewhere, the passage they were found in. A note's hit
     * names its opportunity's {@code projectId}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Hit(Long id, String title, String subtitle, String excerpt, Long projectId) {

        // Spring MVC logs bodies through toString at DEBUG: the id only, never a name or a note (see docs/security.md).
        @Override
        public String toString() {
            return "Hit[id=" + id + "]";
        }
    }
}
