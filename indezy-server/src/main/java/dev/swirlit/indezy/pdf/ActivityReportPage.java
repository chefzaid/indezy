package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.model.ActivityDay;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportLayout;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.PublicHoliday;
import dev.swirlit.indezy.util.FrenchPublicHolidays;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.stream.Collectors;

/** Lays out one activity report: header, parties, the month as a daily table or a calendar grid, totals and signatures. */
final class ActivityReportPage {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final float TITLE = 18;
    private static final float BODY = 9.5f;
    private static final float SMALL = 8;
    private static final float ROW = 14;
    private static final float GRID_ROW = 16;
    private static final float GRID_LABEL = 70;
    private static final float SIGNATURE_HEIGHT = 84;

    private final PdfPageWriter writer;
    private final ActivityReportDocument report;
    private final ActivityReportTemplate template;
    private final DocumentLanguage language;
    private final Locale locale;
    private final Map<LocalDate, ActivityDay> days;
    private final SortedMap<LocalDate, PublicHoliday> holidays;

    ActivityReportPage(PdfPageWriter writer, ActivityReportDocument report) {
        this.writer = writer;
        this.report = report;
        this.template = report.template();
        this.language = template.language();
        this.locale = ActivityReportLabel.locale(language);
        this.days = report.daysByDate();
        this.holidays = FrenchPublicHolidays.in(report.month());
    }

    /** The document title: the client's own, or the standard one. */
    String title() {
        return template.title() != null ? template.title() : ActivityReportLabel.DEFAULT_TITLE.in(language);
    }

    String monthTitle() {
        String month = report.month().getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale);
        return capitalize(month) + " " + report.month().getYear();
    }

    void write() throws IOException {
        header();
        parties();
        if (template.layout() == ActivityReportLayout.CALENDAR_GRID) {
            calendarGrid();
        } else {
            dailyTable();
        }
        totals();
        comment();
        signatures();
        footer();
    }

    private void header() throws IOException {
        writer.moveDown(TITLE);
        writer.text(writer.left(), writer.cursor(), writer.ellipsize(title(), true, TITLE, writer.width() - 150), true, TITLE);
        writer.textRight(writer.right(), writer.cursor(), ActivityReportLabel.of(report.status()).in(language), true, 10);
        writer.moveDown(18);
        writer.text(writer.left(), writer.cursor(), monthTitle(), false, 12);
        writer.moveDown(10);
        writer.line(writer.left(), writer.cursor(), writer.right(), writer.cursor());
    }

    /** Consultant, client, period and ESN in two columns, then the mission on a full-width line. */
    private void parties() throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {ActivityReportLabel.CONSULTANT.in(language), report.consultantName()});
        rows.add(new String[] {ActivityReportLabel.CLIENT.in(language), report.clientName()});
        rows.add(new String[] {ActivityReportLabel.PERIOD.in(language), ActivityReportLabel.PERIOD_RANGE.format(language,
            date(report.month().atDay(1)), date(report.month().atEndOfMonth()))});
        if (report.middlemanName() != null) {
            rows.add(new String[] {ActivityReportLabel.INTERMEDIARY.in(language), report.middlemanName()});
        }
        String[] mission = {ActivityReportLabel.MISSION.in(language), report.missionRole()};
        float labelWidth = 0;
        for (String[] row : rows) {
            labelWidth = Math.max(labelWidth, writer.textWidth(row[0], true, BODY) + 12);
        }
        float column = writer.width() / 2;
        writer.moveDown(6);
        for (int index = 0; index < rows.size(); index++) {
            if (index % 2 == 0) {
                writer.moveDown(ROW + 2);
            }
            float x = writer.left() + (index % 2) * column;
            writer.text(x, writer.cursor(), rows.get(index)[0], true, BODY);
            writer.text(x + labelWidth, writer.cursor(),
                writer.ellipsize(rows.get(index)[1], false, BODY, column - labelWidth - 10), false, BODY);
        }
        writer.moveDown(ROW + 2);
        writer.text(writer.left(), writer.cursor(), mission[0], true, BODY);
        writer.text(writer.left() + labelWidth, writer.cursor(),
            writer.ellipsize(mission[1], false, BODY, writer.width() - labelWidth), false, BODY);
        writer.moveDown(ROW);
    }

    private void dailyTable() throws IOException {
        float[] widths = template.showsLocation() ? new float[] {70, 75, 55, 80} : new float[] {70, 75, 55};
        tableHeader(widths);
        for (LocalDate date : report.month().atDay(1).datesUntil(report.month().atEndOfMonth().plusDays(1)).toList()) {
            if (writer.ensureSpace(ROW)) {
                tableHeader(widths);
            }
            writer.moveDown(ROW);
            float baseline = writer.cursor();
            if (isOff(date)) {
                writer.shade(writer.left(), baseline - 4, writer.width(), ROW);
            }
            ActivityDay day = days.get(date);
            List<String> cells = new ArrayList<>(List.of(date(date), capitalize(weekday(date, TextStyle.FULL)),
                day != null && day.getType().isWorked() ? number(day.getDuration()) : ""));
            if (template.showsLocation()) {
                cells.add(day != null && day.getType().isWorked() ? locationLabel(day.getType()) : "");
            }
            cells.add(remark(date, day));
            float x = writer.left() + 4;
            for (int column = 0; column < cells.size(); column++) {
                float cellWidth = column < widths.length ? widths[column] : writer.right() - x;
                writer.text(x, baseline, writer.ellipsize(cells.get(column), false, BODY, cellWidth - 6), false, BODY);
                x += cellWidth;
            }
            writer.line(writer.left(), baseline - 4, writer.right(), baseline - 4);
        }
    }

    private void tableHeader(float[] widths) throws IOException {
        List<String> headings = new ArrayList<>(List.of(ActivityReportLabel.DATE.in(language),
            ActivityReportLabel.DAY.in(language), ActivityReportLabel.WORKED.in(language)));
        if (template.showsLocation()) {
            headings.add(ActivityReportLabel.LOCATION.in(language));
        }
        headings.add(ActivityReportLabel.REMARK.in(language));
        writer.moveDown(ROW + 4);
        writer.shade(writer.left(), writer.cursor() - 4, writer.width(), ROW);
        float x = writer.left() + 4;
        for (int column = 0; column < headings.size(); column++) {
            writer.text(x, writer.cursor(), headings.get(column), true, BODY);
            x += column < widths.length ? widths[column] : 0;
        }
    }

    private void calendarGrid() throws IOException {
        List<LocalDate> dates = report.month().atDay(1).datesUntil(report.month().atEndOfMonth().plusDays(1)).toList();
        List<String> labels = new ArrayList<>(List.of(ActivityReportLabel.GRID_DAY.in(language), "",
            ActivityReportLabel.WORKED.in(language)));
        if (template.showsLocation()) {
            labels.add(ActivityReportLabel.LOCATION.in(language));
        }
        labels.add(ActivityReportLabel.GRID_ABSENCE.in(language));
        float height = labels.size() * GRID_ROW;
        float cell = (writer.width() - GRID_LABEL) / dates.size();
        writer.ensureSpace(height + 40);
        writer.moveDown(12);
        float top = writer.cursor();
        for (int index = 0; index < dates.size(); index++) {
            LocalDate date = dates.get(index);
            float x = writer.left() + GRID_LABEL + index * cell;
            if (isOff(date)) {
                writer.shade(x, top - height, cell, height);
            }
            List<String> values = gridColumn(date);
            for (int row = 0; row < values.size(); row++) {
                writer.textCentered(x + cell / 2, top - (row + 1) * GRID_ROW + 5, values.get(row), row == 0, SMALL);
            }
            writer.line(x, top, x, top - height);
        }
        for (int row = 0; row < labels.size(); row++) {
            float baseline = top - (row + 1) * GRID_ROW;
            writer.text(writer.left() + 3, baseline + 5, labels.get(row), true, SMALL);
            writer.line(writer.left(), baseline, writer.right(), baseline);
        }
        writer.box(writer.left(), top - height, writer.width(), height);
        writer.moveDown(height + 4);
        writer.paragraph(ActivityReportLabel.GRID_LEGEND.format(language, ActivityReportLabel.GRID_ONSITE_CODE.in(language),
            ActivityReportLabel.GRID_REMOTE_CODE.in(language), ActivityReportLabel.GRID_HOLIDAY_CODE.in(language)),
            false, SMALL, writer.width());
        if (!holidays.isEmpty()) {
            String names = holidays.entrySet().stream()
                .map(entry -> date(entry.getKey()) + " " + ActivityReportLabel.holidayName(entry.getValue(), language))
                .collect(Collectors.joining(", "));
            writer.paragraph(ActivityReportLabel.HOLIDAYS_OF_MONTH.format(language, names), false, SMALL, writer.width());
        }
    }

    /** One day of the grid: its number, weekday initial, worked share, location code (when shown) and absence. */
    private List<String> gridColumn(LocalDate date) {
        ActivityDay day = days.get(date);
        boolean worked = day != null && day.getType().isWorked();
        boolean absent = day != null && day.getType() == ActivityDayType.ABSENCE;
        String workedCell = worked ? number(day.getDuration()) : "";
        if (!worked && holidays.containsKey(date)) {
            workedCell = ActivityReportLabel.GRID_HOLIDAY_CODE.in(language);
        }
        List<String> values = new ArrayList<>(List.of(String.valueOf(date.getDayOfMonth()), weekday(date, TextStyle.NARROW),
            workedCell));
        if (template.showsLocation()) {
            values.add(worked ? locationCode(day.getType()) : "");
        }
        values.add(absent ? number(day.getDuration()) : "");
        return values;
    }

    private void totals() throws IOException {
        writer.ensureSpace(ROW * 4);
        writer.moveDown(ROW + 6);
        String worked = ActivityReportLabel.WORKED_TOTAL.format(language, number(report.workedDays()));
        writer.text(writer.left(), writer.cursor(), worked, true, 10);
        if (template.showsLocation() && report.workedDays() > 0) {
            writer.text(writer.left() + writer.textWidth(worked, true, 10) + 6, writer.cursor(),
                ActivityReportLabel.WORKED_SPLIT.format(language, number(report.daysOf(ActivityDayType.ONSITE)),
                    number(report.daysOf(ActivityDayType.REMOTE))), false, 10);
        }
        writer.moveDown(ROW);
        writer.text(writer.left(), writer.cursor(),
            ActivityReportLabel.ABSENCE_TOTAL.format(language, number(report.daysOf(ActivityDayType.ABSENCE))), false, 10);
        writer.moveDown(ROW);
        writer.text(writer.left(), writer.cursor(),
            ActivityReportLabel.BUSINESS_DAYS.format(language, FrenchPublicHolidays.businessDays(report.month())), false, 10);
    }

    private void comment() throws IOException {
        if (report.comment() == null || report.comment().isBlank()) {
            return;
        }
        writer.ensureSpace(ROW * 3);
        writer.moveDown(ROW + 4);
        writer.text(writer.left(), writer.cursor(), ActivityReportLabel.COMMENT.in(language), true, 10);
        writer.paragraph(report.comment(), false, BODY, writer.width());
    }

    private void signatures() throws IOException {
        writer.ensureSpace(SIGNATURE_HEIGHT + 20);
        writer.moveDown(18);
        float gap = 16;
        float boxWidth = (writer.width() - gap) / 2;
        float top = writer.cursor();
        float bottom = top - SIGNATURE_HEIGHT;
        float clientX = writer.left() + boxWidth + gap;
        writer.box(writer.left(), bottom, boxWidth, SIGNATURE_HEIGHT);
        writer.box(clientX, bottom, boxWidth, SIGNATURE_HEIGHT);
        writer.text(writer.left() + 8, top - 15, ActivityReportLabel.CONSULTANT_SIGNATURE.in(language), true, 10);
        writer.text(writer.left() + 8, top - 30, writer.ellipsize(report.consultantName(), false, BODY, boxWidth - 16), false, BODY);
        writer.text(clientX + 8, top - 15, ActivityReportLabel.CLIENT_SIGNATURE.in(language), true, 10);
        List<String> decision = decisionLines(boxWidth - 16);
        if (decision.isEmpty()) {
            writer.text(clientX + 8, bottom + 8, ActivityReportLabel.SIGNATURE_HINT.in(language), false, SMALL);
        }
        for (int index = 0; index < decision.size(); index++) {
            writer.text(clientX + 8, top - 30 - index * 12, decision.get(index), index == 0, BODY);
        }
        writer.moveDown(SIGNATURE_HEIGHT);
    }

    /** Who approved or rejected the report, when (Paris time) and why, wrapped into the signature box. */
    private List<String> decisionLines(float maxWidth) throws IOException {
        boolean decided = report.status() == ActivityReportStatus.VALIDATED || report.status() == ActivityReportStatus.REJECTED;
        if (!decided || report.decidedAt() == null) {
            return List.of();
        }
        boolean validated = report.status() == ActivityReportStatus.VALIDATED;
        ZonedDateTime decidedAt = report.decidedAt().atZone(PARIS);
        List<String> lines = new ArrayList<>();
        lines.add(writer.ellipsize((validated ? ActivityReportLabel.VALIDATED_BY : ActivityReportLabel.REJECTED_BY)
            .format(language, report.validatorName()), true, BODY, maxWidth));
        lines.add(ActivityReportLabel.DECIDED_AT.format(language, date(decidedAt.toLocalDate()),
            decidedAt.format(DateTimeFormatter.ofPattern("HH:mm"))));
        if (report.decisionComment() != null) {
            String note = (validated ? ActivityReportLabel.VALIDATOR_COMMENT : ActivityReportLabel.REASON)
                .format(language, report.decisionComment());
            List<String> wrapped = writer.wrap(note, false, BODY, maxWidth);
            lines.add(wrapped.get(0));
            if (wrapped.size() > 1) {
                lines.add(writer.ellipsize(String.join(" ", wrapped.subList(1, wrapped.size())), false, BODY, maxWidth));
            }
        }
        return lines;
    }

    private void footer() throws IOException {
        if (template.footer() == null) {
            return;
        }
        writer.moveDown(8);
        writer.paragraph(template.footer(), false, SMALL, writer.width());
    }

    private String remark(LocalDate date, ActivityDay day) {
        if (day != null && day.getType() == ActivityDayType.ABSENCE) {
            return (day.isHalfDay() ? ActivityReportLabel.HALF_DAY_ABSENCE : ActivityReportLabel.ABSENCE).in(language);
        }
        PublicHoliday holiday = holidays.get(date);
        if (holiday == null) {
            return "";
        }
        return ActivityReportLabel.HOLIDAY.format(language, ActivityReportLabel.holidayName(holiday, language));
    }

    private boolean isOff(LocalDate date) {
        return FrenchPublicHolidays.isWeekend(date) || holidays.containsKey(date);
    }

    private String locationLabel(ActivityDayType type) {
        return (type == ActivityDayType.ONSITE ? ActivityReportLabel.ONSITE : ActivityReportLabel.REMOTE).in(language);
    }

    private String locationCode(ActivityDayType type) {
        return (type == ActivityDayType.ONSITE ? ActivityReportLabel.GRID_ONSITE_CODE : ActivityReportLabel.GRID_REMOTE_CODE)
            .in(language);
    }

    private String weekday(LocalDate date, TextStyle style) {
        return date.getDayOfWeek().getDisplayName(style, locale);
    }

    private String date(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale));
    }

    private String number(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(1);
        return format.format(value);
    }

    private String capitalize(String text) {
        return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(locale) + text.substring(1);
    }
}
