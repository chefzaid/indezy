package dev.swirlit.indezy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The sections of an Indezy account export that an import brings into a workspace; the export's other sections (profile,
 * activity reports, offers, templates and so on) are read past.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkspaceImportFile(
    List<SourceDto> sources,
    List<ClientDto> clients,
    List<ContactDto> contacts,
    List<SeasonDto> seasons,
    List<ProjectDto> projects,
    List<ProjectNoteDto> projectNotes) {

    public WorkspaceImportFile {
        sources = List.copyOf(present(sources));
        clients = List.copyOf(present(clients));
        contacts = List.copyOf(present(contacts));
        seasons = List.copyOf(present(seasons));
        projects = List.copyOf(present(projects));
        projectNotes = List.copyOf(present(projectNotes));
    }

    /** Whether the file holds none of the sections an import reads. */
    public boolean isEmpty() {
        return sources.isEmpty() && clients.isEmpty() && contacts.isEmpty() && seasons.isEmpty() && projects.isEmpty() &&
            projectNotes.isEmpty();
    }

    /** The section's records, a missing section and null entries aside. */
    private static <T> List<T> present(List<T> items) {
        return items == null ? List.of() : items.stream().filter(java.util.Objects::nonNull).toList();
    }
}
