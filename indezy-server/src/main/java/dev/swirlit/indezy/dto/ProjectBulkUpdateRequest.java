package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.LostReason;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * One change applied to several opportunities at once, from the Kanban board's selection: a status (with the lost
 * reason when it is LOST), a source, or the archive.
 */
public record ProjectBulkUpdateRequest(
    @NotEmpty @Size(max = 200) List<@NotNull Long> projectIds,
    @NotNull Action action,
    ProjectStatus status,
    LostReason lostReason,
    Long sourceId) {

    public enum Action { STATUS, SOURCE, ARCHIVE }

    public ProjectBulkUpdateRequest {
        projectIds = projectIds == null ? null : List.copyOf(projectIds);
    }
}
