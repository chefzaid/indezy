package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds an accountant-friendly CSV summary of a freelance's projects, optionally limited to a
 * single year (by project start date). Kept separate from project CRUD so the reporting/export
 * concern stays cohesive, mirroring {@link DashboardStatsService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectExportService {

    private final ProjectRepository projectRepository;

    private static final String[] HEADERS = {
        "Role", "Status", "Client", "Source", "Work mode", "Start date",
        "Duration (months)", "Daily rate (EUR)", "Days per year", "Estimated revenue (EUR)"
    };
    private static final String LINE_SEPARATOR = "\r\n";

    /**
     * Builds a CSV summary of the freelance's projects. When {@code year} is non-null only projects
     * starting in that year are included. Rows are ordered by start date then role, and a final
     * Total row sums the estimated revenue.
     */
    @Transactional(readOnly = true)
    public String buildYearlySummaryCsv(Long freelanceId, Integer year) {
        log.debug("Building CSV summary for freelance {} and year {}", freelanceId, year);
        List<Project> projects = projectRepository.findByFreelanceId(freelanceId).stream()
            .filter(project -> year == null
                || (project.getStartDate() != null && project.getStartDate().getYear() == year))
            .sorted(Comparator
                .comparing(Project::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(project -> project.getRole() == null ? "" : project.getRole()))
            .toList();

        StringBuilder csv = new StringBuilder();
        csv.append(csvRow(HEADERS)).append(LINE_SEPARATOR);

        long totalRevenue = 0;
        for (Project project : projects) {
            Integer revenue = project.getTotalRevenue();
            if (revenue != null) {
                totalRevenue += revenue;
            }
            csv.append(csvRow(
                project.getRole(),
                project.getStatus() != null ? project.getStatus().name() : "",
                project.getClient() != null ? project.getClient().getCompanyName() : "",
                project.getSource() != null ? project.getSource().getName() : "",
                project.getWorkMode() != null ? project.getWorkMode().name() : "",
                project.getStartDate() != null ? project.getStartDate().toString() : "",
                asText(project.getDurationInMonths()),
                asText(project.getDailyRate()),
                asText(project.getDaysPerYear()),
                asText(revenue)
            )).append(LINE_SEPARATOR);
        }

        csv.append(csvRow("Total", "", "", "", "", "", "", "", "", String.valueOf(totalRevenue)))
            .append(LINE_SEPARATOR);
        return csv.toString();
    }

    private String asText(Integer value) {
        return value != null ? value.toString() : "";
    }

    private String csvRow(String... fields) {
        return Arrays.stream(fields).map(this::escape).collect(Collectors.joining(","));
    }

    /** Quotes a field when it contains a comma, quote or newline, doubling any embedded quotes. */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
