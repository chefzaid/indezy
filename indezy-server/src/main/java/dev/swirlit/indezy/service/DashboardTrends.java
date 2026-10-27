package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.StepStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Predicate;

/**
 * The dashboard's trends (see docs/features.md#trends): the last {@link #WINDOW_DAYS} days against the same length
 * just before, for opportunities added, interviews held, missions won and the daily rate they were won at.
 */
final class DashboardTrends {

    static final int WINDOW_DAYS = 90;

    private DashboardTrends() {
    }

    /**
     * @param steps      the interview steps of at least the last {@code 2 × WINDOW_DAYS} days
     * @param signatures each won mission's latest validated step, when it has one
     */
    static List<DashboardStatsDto.Trend> build(List<Project> projects, List<InterviewStep> steps, Map<Long, LocalDateTime> signatures,
                                               LocalDate today) {
        LocalDate start = today.minusDays(WINDOW_DAYS - 1L);
        LocalDate previousStart = start.minusDays(WINDOW_DAYS);
        Predicate<LocalDate> current = date -> date != null && !date.isBefore(start) && !date.isAfter(today);
        Predicate<LocalDate> previous = date -> date != null && !date.isBefore(previousStart) && date.isBefore(start);

        List<LocalDate> added = projects.stream().map(Project::getCreatedAt).filter(Objects::nonNull).map(LocalDateTime::toLocalDate).toList();
        List<LocalDate> held = steps.stream().filter(step -> step.getDate() != null && step.getStatus() != StepStatus.CANCELED)
            .map(step -> step.getDate().toLocalDate()).toList();
        List<Project> won = projects.stream().filter(project -> project.getStatus() == ProjectStatus.WON).toList();

        return List.of(
            count("OPPORTUNITIES", added, current, previous),
            count("INTERVIEWS", held, current, previous),
            count("WON", won.stream().map(project -> wonOn(project, signatures)).toList(), current, previous),
            trend("OBTAINED_RATE", averageRate(won, signatures, current), averageRate(won, signatures, previous)));
    }

    /** When a mission was won: its latest validated interview step, or else its start. */
    private static LocalDate wonOn(Project project, Map<Long, LocalDateTime> signatures) {
        LocalDateTime signed = signatures.get(project.getId());
        return signed != null ? signed.toLocalDate() : project.getStartDate();
    }

    private static DashboardStatsDto.Trend count(String metric, List<LocalDate> dates, Predicate<LocalDate> current,
                                                 Predicate<LocalDate> previous) {
        return trend(metric, (double) dates.stream().filter(current).count(), (double) dates.stream().filter(previous).count());
    }

    private static Double averageRate(List<Project> won, Map<Long, LocalDateTime> signatures, Predicate<LocalDate> window) {
        OptionalDouble average = won.stream().filter(project -> project.getDailyRate() != null && window.test(wonOn(project, signatures)))
            .mapToInt(Project::getDailyRate).average();
        return average.isPresent() ? (double) Math.round(average.getAsDouble()) : null;
    }

    /** The change in percent, rounded; none when there is nothing to compare with. */
    private static DashboardStatsDto.Trend trend(String metric, Double current, Double previous) {
        Integer change = current == null || previous == null || previous == 0 ? null : (int) Math.round((current - previous) * 100 / previous);
        return new DashboardStatsDto.Trend(metric, current, previous, change);
    }
}
