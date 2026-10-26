package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The records picked in a list, to delete at once. */
public record BulkDeleteRequest(@NotEmpty @Size(max = 200) List<@NotNull Long> ids) {

    public BulkDeleteRequest {
        ids = ids == null ? null : List.copyOf(ids);
    }
}
