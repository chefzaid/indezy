package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.DashboardWidget;
import jakarta.validation.constraints.NotNull;

/** One block of the dashboard, in the account's order, shown or hidden. */
public record DashboardWidgetDto(@NotNull DashboardWidget id, boolean visible) {
}
