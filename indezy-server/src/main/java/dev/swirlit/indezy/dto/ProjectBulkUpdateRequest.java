package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.RefusedBy;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One change applied to several opportunities at once, from the Kanban board's or the list's selection: a status (with
 * who refused them and why when it is LOST), a source, a tag added to their tech stack, the archive, or deleting them.
 */
public record ProjectBulkUpdateRequest(
    @NotEmpty @Size(max = 200) List<@NotNull Long> projectIds,
    @NotNull Action action,
    ProjectStatus status,
    RefusedBy refusedBy,
    LostReason lostReason,
    Long sourceId,
    @Size(max = 60) String tag) {

    public enum Action { STATUS, SOURCE, TAG, ARCHIVE, DELETE }

    public ProjectBulkUpdateRequest {
        projectIds = projectIds == null ? null : List.copyOf(projectIds);
    }
}
