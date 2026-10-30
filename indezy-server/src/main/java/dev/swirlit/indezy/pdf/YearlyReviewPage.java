package dev.swirlit.indezy.pdf;

import dev.swirlit.indezy.dto.YearlyReviewDto;
import dev.swirlit.indezy.dto.YearlyReviewDto.Mission;
import dev.swirlit.indezy.dto.YearlyReviewDto.Totals;
import dev.swirlit.indezy.model.enums.DocumentLanguage;

import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.stream.Collectors;

/** Lays out a yearly review: title, four key figures, the missions, the opportunities, the skills and the network. */
final class YearlyReviewPage {

    private static final float TITLE = 20;
    private static final float HEADING = 12.5f;
    private static final float BODY = 9.5f;
    private static final float SMALL = 8;
    private static final float ROW = 15;
    private static final float FIGURE_HEIGHT = 58;

    private final PdfPageWriter writer;
    private final YearlyReviewDto review;
    private final DocumentLanguage language;
    private final Locale locale;

    YearlyReviewPage(PdfPageWriter writer, YearlyReviewDto review, DocumentLanguage language) {
        this.writer = writer;
        this.review = review;
        this.language = language;
        this.locale = language.locale();
    }

    String title() {
        return YearlyReviewLabel.TITLE.format(language, review.year());
    }

    void write(LocalDate today) throws IOException {
        header();
        figures();
        missions();
        pipeline();
        skills();
        network();
        writer.ensureSpace(ROW * 2);
        writer.moveDown(ROW * 2);
        writer.text(writer.left(), writer.cursor(), YearlyReviewLabel.FOOTER.format(language, date(today)), false, SMALL);
    }

    private void header() throws IOException {
        writer.moveDown(TITLE);
        writer.text(writer.left(), writer.cursor(), title(), true, TITLE);
        writer.textRight(writer.right(), writer.cursor(), writer.ellipsize(review.name(), false, 11, writer.width() / 2), false, 11);
        writer.moveDown(10);
        writer.line(writer.left(), writer.cursor(), writer.right(), writer.cursor());
    }

    /** Revenue (with the change since the year before), days, average rate and missions, in four shaded boxes. */
    private void figures() throws IOException {
        Totals totals = review.totals();
        Totals before = review.previousYear();
        String change = before.revenue() > 0 ?
            YearlyReviewLabel.VERSUS.format(language, percent(totals.revenue(), before.revenue()), review.year() - 1) :
            YearlyReviewLabel.NO_COMPARISON.format(language, review.year() - 1);
        String[][] figures = {
            {YearlyReviewLabel.REVENUE.in(language), euros(totals.revenue()), change},
            {YearlyReviewLabel.DAYS.in(language), days(totals.days()), ""},
            {YearlyReviewLabel.AVERAGE_RATE.in(language), totals.averageDailyRate() == null ? "—" : euros(totals.averageDailyRate()), ""},
            {YearlyReviewLabel.MISSIONS.in(language), String.valueOf(totals.missions()), ""}
        };
        writer.moveDown(16 + FIGURE_HEIGHT);
        float gap = 8;
        float boxWidth = (writer.width() - gap * (figures.length - 1)) / figures.length;
        for (int index = 0; index < figures.length; index++) {
            float x = writer.left() + index * (boxWidth + gap);
            writer.shade(x, writer.cursor(), boxWidth, FIGURE_HEIGHT);
            writer.text(x + 8, writer.cursor() + FIGURE_HEIGHT - 16, writer.ellipsize(figures[index][0], false, SMALL, boxWidth - 16),
                false, SMALL);
            writer.text(x + 8, writer.cursor() + FIGURE_HEIGHT - 36, writer.ellipsize(figures[index][1], true, 15, boxWidth - 16),
                true, 15);
            writer.text(x + 8, writer.cursor() + 8, writer.ellipsize(figures[index][2], false, SMALL, boxWidth - 16), false, SMALL);
        }
    }

    private void missions() throws IOException {
        heading(YearlyReviewLabel.MISSIONS_TITLE);
        if (review.missions().isEmpty()) {
            line(YearlyReviewLabel.NO_MISSIONS.in(language));
            return;
        }
        float[] columns = {0, 0.48f, 0.72f, 0.81f, 0.9f};
        String[] titles = {YearlyReviewLabel.COLUMN_MISSION.in(language), YearlyReviewLabel.COLUMN_PERIOD.in(language),
            YearlyReviewLabel.COLUMN_DAYS.in(language), YearlyReviewLabel.COLUMN_RATE.in(language),
            YearlyReviewLabel.COLUMN_REVENUE.in(language)};
        writer.moveDown(ROW);
        row(columns, titles, true);
        writer.line(writer.left(), writer.cursor() - 4, writer.right(), writer.cursor() - 4);
        boolean estimated = false;
        for (Mission mission : review.missions()) {
            estimated |= !mission.reported();
            writer.ensureSpace(ROW);
            writer.moveDown(ROW);
            String name = mission.clientName() == null ? mission.role() : mission.role() + " · " + mission.clientName();
            String period = date(mission.start()) + " – " + (mission.end() == null ? YearlyReviewLabel.ONGOING.in(language) : date(mission.end()));
            row(columns, new String[] {name, period, days(mission.days()) + (mission.reported() ? "" : "*"),
                mission.dailyRate() == null ? "—" : euros(mission.dailyRate()),
                mission.revenue() == null ? "—" : euros(mission.revenue())}, false);
        }
        if (estimated) {
            writer.moveDown(ROW);
            writer.text(writer.left(), writer.cursor(), YearlyReviewLabel.ESTIMATED.in(language), false, SMALL);
        }
    }

    private void pipeline() throws IOException {
        YearlyReviewDto.Pipeline pipeline = review.pipeline();
        heading(YearlyReviewLabel.PIPELINE_TITLE);
        line(YearlyReviewLabel.PIPELINE.format(language, pipeline.opened(), pipeline.won(), pipeline.lost()));
        if (pipeline.conversionRate() != null) {
            line(YearlyReviewLabel.CONVERSION.format(language, pipeline.conversionRate()));
        }
        if (!pipeline.lostReasons().isEmpty()) {
            line(YearlyReviewLabel.LOST_REASONS.format(language, pipeline.lostReasons().stream()
                .map(reason -> YearlyReviewLabel.reason(reason.reason(), language) + " (" + reason.count() + ")")
                .collect(Collectors.joining(", "))));
        }
    }

    private void skills() throws IOException {
        heading(YearlyReviewLabel.SKILLS_TITLE);
        if (review.topSkills().isEmpty()) {
            line(YearlyReviewLabel.NO_SKILLS.in(language));
        }
        for (YearlyReviewDto.SkillCount skill : review.topSkills()) {
            line("• " + YearlyReviewLabel.SKILL.format(language, skill.skill(), skill.count()));
        }
    }

    private void network() throws IOException {
        YearlyReviewDto.Network network = review.network();
        heading(YearlyReviewLabel.NETWORK_TITLE);
        line(YearlyReviewLabel.NETWORK.format(language, network.clients(), network.esns(), network.contacts(), network.exchanges()));
    }

    private void heading(YearlyReviewLabel label) throws IOException {
        writer.ensureSpace(ROW * 3);
        writer.moveDown(ROW * 2);
        writer.text(writer.left(), writer.cursor(), label.in(language), true, HEADING);
        writer.moveDown(4);
    }

    /** Writes the text on as many lines as it takes: a year's lost reasons can run past one. */
    private void line(String text) throws IOException {
        for (String part : writer.wrap(text, false, BODY, writer.width())) {
            writer.ensureSpace(ROW);
            writer.moveDown(ROW);
            writer.text(writer.left(), writer.cursor(), part, false, BODY);
        }
    }

    private void row(float[] columns, String[] cells, boolean bold) throws IOException {
        for (int index = 0; index < cells.length; index++) {
            float x = writer.left() + columns[index] * writer.width();
            float next = index + 1 < columns.length ? writer.left() + columns[index + 1] * writer.width() : writer.right();
            writer.text(x, writer.cursor(), writer.ellipsize(cells[index], bold, BODY, next - x - 6), bold, BODY);
        }
    }

    private String euros(long amount) {
        // The fonts draw no narrow no-break space: French groups digits with a plain one.
        return NumberFormat.getIntegerInstance(locale).format(amount).replace(' ', ' ').replace(' ', ' ') + " €";
    }

    private String days(double days) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(1);
        return format.format(days).replace(' ', ' ').replace(' ', ' ');
    }

    private String percent(long now, long before) {
        long change = Math.round((now - before) * 100.0 / before);
        return (change > 0 ? "+" : "") + change + (language == DocumentLanguage.EN ? "%" : " %");
    }

    private String date(LocalDate day) {
        return day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)).replace(' ', ' ');
    }
}
