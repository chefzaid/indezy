package dev.swirlit.indezy.util;

import dev.swirlit.indezy.dto.DashboardWidgetDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.swirlit.indezy.model.enums.DashboardWidget.ANALYTICS;
import static dev.swirlit.indezy.model.enums.DashboardWidget.CHARTS;
import static dev.swirlit.indezy.model.enums.DashboardWidget.OFFERS;
import static dev.swirlit.indezy.model.enums.DashboardWidget.RECENT;
import static dev.swirlit.indezy.model.enums.DashboardWidget.REMINDERS;
import static dev.swirlit.indezy.model.enums.DashboardWidget.STATS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardLayoutTest {

    @Test
    void showsEveryBlockInItsUsualPlaceUntilArranged() {
        assertThat(DashboardLayout.read(null)).extracting(DashboardWidgetDto::id).containsExactly(STATS, CHARTS, REMINDERS, OFFERS, ANALYTICS, RECENT);
        assertThat(DashboardLayout.read(null)).allMatch(DashboardWidgetDto::visible);
    }

    @Test
    void readsTheArrangementBackWithTheBlocksAddedSinceAtTheEnd() {
        String stored = DashboardLayout.write(List.of(new DashboardWidgetDto(OFFERS, true), new DashboardWidgetDto(STATS, false)));
        assertThat(stored).isEqualTo("OFFERS,!STATS");

        assertThat(DashboardLayout.read(stored + ",GONE,OFFERS,!")).containsExactly(new DashboardWidgetDto(OFFERS, true),
            new DashboardWidgetDto(STATS, false), new DashboardWidgetDto(CHARTS, true), new DashboardWidgetDto(REMINDERS, true),
            new DashboardWidgetDto(ANALYTICS, true), new DashboardWidgetDto(RECENT, true));
    }

    @Test
    void refusesABlockListedTwice() {
        assertThatThrownBy(() -> DashboardLayout.write(List.of(new DashboardWidgetDto(STATS, true), new DashboardWidgetDto(STATS, false))))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
