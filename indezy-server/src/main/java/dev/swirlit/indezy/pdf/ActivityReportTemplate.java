package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.enums.ActivityReportLayout;
import dev.swirlit.indezy.model.enums.DocumentLanguage;

import java.util.Objects;

/**
 * How a client wants its activity reports printed. A mission uses the template of its ESN when it has one, otherwise
 * the one of its final client; unset fields fall back to the default title, the daily table, the location column and
 * French.
 */
public record ActivityReportTemplate(String title, ActivityReportLayout layout, boolean showsLocation,
                                     DocumentLanguage language, String footer) {

    public ActivityReportTemplate {
        layout = Objects.requireNonNullElse(layout, ActivityReportLayout.DAILY_TABLE);
        language = Objects.requireNonNullElse(language, DocumentLanguage.FR);
        title = blankToNull(title);
        footer = blankToNull(footer);
    }

    public static ActivityReportTemplate of(Client client) {
        if (client == null) {
            return new ActivityReportTemplate(null, null, true, null, null);
        }
        return new ActivityReportTemplate(client.getActivityReportTitle(), client.getActivityReportLayout(),
            !Boolean.FALSE.equals(client.getActivityReportShowsLocation()), client.getActivityReportLanguage(),
            client.getActivityReportFooter());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
