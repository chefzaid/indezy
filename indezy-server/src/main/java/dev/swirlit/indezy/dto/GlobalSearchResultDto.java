package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** What the global search found in a workspace, by kind, each list at most a few hits, best first. */
public record GlobalSearchResultDto(List<Hit> projects, List<Hit> clients, List<Hit> contacts, List<Hit> notes, List<Hit> members,
                                    List<Hit> posts) {

    public GlobalSearchResultDto {
        projects = List.copyOf(projects);
        clients = List.copyOf(clients);
        contacts = List.copyOf(contacts);
        notes = List.copyOf(notes);
        members = List.copyOf(members);
        posts = List.copyOf(posts);
    }

    /**
     * One record found: its title (an opportunity's role, a client's or contact's name, a note's opportunity), what places
     * it (a client, a city), and, when the words were found elsewhere, the passage they were found in. A note's hit
     * names its opportunity's {@code projectId}; a member's or a post's names the member's {@code handle}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Hit(Long id, String title, String subtitle, String excerpt, Long projectId, String handle) {

        public Hit(Long id, String title, String subtitle, String excerpt, Long projectId) {
            this(id, title, subtitle, excerpt, projectId, null);
        }

        // Spring MVC logs bodies through toString at DEBUG: the id only, never a name or a note (see docs/security.md).
        @Override
        public String toString() {
            return "Hit[id=" + id + "]";
        }
    }
}
