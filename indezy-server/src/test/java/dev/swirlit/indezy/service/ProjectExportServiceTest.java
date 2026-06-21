package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectExportServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectExportService projectExportService;

    @Test
    void buildYearlySummaryCsv_ShouldFilterByYearAndSumRevenue() {
        // Given a 2025 project (revenue 600*220*6/12 = 66,000) and a 2024 project that must be excluded.
        Project in2025 = project("Backend Dev", ProjectStatus.WON, "Acme", "Malt",
            WorkMode.REMOTE, LocalDate.of(2025, 3, 1), 6, 600, 220);
        Project in2024 = project("Old Mission", ProjectStatus.WON, "Globex", "LinkedIn",
            WorkMode.ONSITE, LocalDate.of(2024, 1, 1), 12, 500, 240);
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(in2025, in2024));

        // When
        String csv = projectExportService.buildYearlySummaryCsv(1L, 2025);

        // Then only the 2025 row plus header and totals are present.
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[0]).isEqualTo(
            "Role,Status,Client,Source,Work mode,Start date,Duration (months),"
            + "Daily rate (EUR),Days per year,Estimated revenue (EUR)");
        assertThat(lines[1]).isEqualTo("Backend Dev,WON,Acme,Malt,REMOTE,2025-03-01,6,600,220,66000");
        assertThat(lines[2]).isEqualTo("Total,,,,,,,,,66000");
    }

    @Test
    void buildYearlySummaryCsv_WithoutYear_ShouldIncludeAllAndQuoteSpecialCharacters() {
        // Given a project whose role contains a comma, requiring CSV quoting.
        Project project = project("Lead, Platform", ProjectStatus.APPLIED, "Acme", null,
            null, LocalDate.of(2025, 5, 1), null, 700, null);
        when(projectRepository.findByFreelanceId(1L)).thenReturn(List.of(project));

        // When no year is given, all projects are exported.
        String csv = projectExportService.buildYearlySummaryCsv(1L, null);

        // Then the comma-containing field is wrapped in quotes and empty fields stay blank.
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(3);
        assertThat(lines[1]).isEqualTo("\"Lead, Platform\",APPLIED,Acme,,,2025-05-01,,700,,");
        assertThat(lines[2]).isEqualTo("Total,,,,,,,,,0");
    }

    private Project project(String role, ProjectStatus status, String clientName, String sourceName,
                            WorkMode workMode, LocalDate startDate, Integer durationInMonths,
                            Integer dailyRate, Integer daysPerYear) {
        Project project = new Project();
        project.setRole(role);
        project.setStatus(status);
        if (clientName != null) {
            Client client = new Client();
            client.setCompanyName(clientName);
            project.setClient(client);
        }
        if (sourceName != null) {
            Source source = new Source();
            source.setName(sourceName);
            project.setSource(source);
        }
        project.setWorkMode(workMode);
        project.setStartDate(startDate);
        project.setDurationInMonths(durationInMonths);
        project.setDailyRate(dailyRate);
        project.setDaysPerYear(daysPerYear);
        return project;
    }
}
