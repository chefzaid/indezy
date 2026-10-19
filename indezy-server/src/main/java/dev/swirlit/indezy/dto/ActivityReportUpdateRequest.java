package dev.swirlit.indezy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The days and comment of a month's activity report, replacing what was saved before. */
@Data
@NoArgsConstructor
public class ActivityReportUpdateRequest {

    @Size(max = 1000, message = "Comment must be at most 1000 characters")
    private String comment;

    @NotNull(message = "Days are required")
    @Size(max = 31, message = "A month has at most 31 days")
    private List<@Valid @NotNull ActivityDayDto> days;
}
