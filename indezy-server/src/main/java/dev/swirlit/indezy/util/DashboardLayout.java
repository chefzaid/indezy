package dev.swirlit.indezy.util;

import dev.swirlit.indezy.dto.DashboardWidgetDto;
import dev.swirlit.indezy.model.enums.DashboardWidget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The dashboard's blocks as the account arranged them, kept as one text column: their names in order, a hidden one
 * after {@code !} ("STATS,!CHARTS,REMINDERS"). Reading drops names it does not know and adds, shown, the blocks the
 * account never arranged (none at first, or a block added since), in their usual place at the end.
 */
public final class DashboardLayout {

    private static final String HIDDEN = "!";

    private DashboardLayout() {
    }

    public static List<DashboardWidgetDto> read(String stored) {
        List<DashboardWidgetDto> widgets = new ArrayList<>();
        Set<DashboardWidget> placed = EnumSet.noneOf(DashboardWidget.class);
        if (stored != null) {
            for (String part : stored.split(",")) {
                boolean hidden = part.startsWith(HIDDEN);
                String name = hidden ? part.substring(HIDDEN.length()) : part;
                Arrays.stream(DashboardWidget.values()).filter(widget -> widget.name().equals(name) && placed.add(widget))
                    .findFirst().ifPresent(widget -> widgets.add(new DashboardWidgetDto(widget, !hidden)));
            }
        }
        Arrays.stream(DashboardWidget.values()).filter(widget -> !placed.contains(widget))
            .forEach(widget -> widgets.add(new DashboardWidgetDto(widget, true)));
        return widgets;
    }

    /** The column for these blocks; each named once at most. */
    public static String write(List<DashboardWidgetDto> widgets) {
        Set<DashboardWidget> seen = EnumSet.noneOf(DashboardWidget.class);
        for (DashboardWidgetDto widget : widgets) {
            if (!seen.add(widget.id())) {
                throw new IllegalArgumentException("A dashboard block is listed twice: " + widget.id());
            }
        }
        return widgets.stream().map(widget -> (widget.visible() ? "" : HIDDEN) + widget.id().name()).collect(Collectors.joining(","));
    }
}
