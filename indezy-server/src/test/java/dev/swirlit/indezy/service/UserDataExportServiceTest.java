package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ActivityDayDto;
import dev.swirlit.indezy.dto.ActivityReportDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.dto.UserDto;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDataExportServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private ProjectService projectService;
    @Mock
    private ClientService clientService;
    @Mock
    private ContactService contactService;
    @Mock
    private SourceService sourceService;
    @Mock
    private ActivityReportService activityReportService;

    @InjectMocks
    private UserDataExportService userDataExportService;

    @Test
    void exportUserData_ShouldSerializeProfileAndOwnedEntities() {
        UserDto profile = new UserDto();
        profile.setFirstName("John");
        profile.setEmail("john.doe@example.com");
        when(userService.getUserProfile(1L)).thenReturn(profile);

        ProjectDto project = new ProjectDto();
        project.setRole("Backend Dev");
        when(projectService.findByFreelanceId(1L)).thenReturn(List.of(project));
        when(clientService.findByFreelanceId(1L)).thenReturn(List.of());
        when(contactService.findByFreelanceId(1L)).thenReturn(List.of());
        when(sourceService.findByFreelanceId(1L)).thenReturn(List.of());
        ActivityReportDto report = new ActivityReportDto();
        report.setYear(2026);
        report.setMonth(10);
        report.setStatus(ActivityReportStatus.VALIDATED);
        report.setDays(List.of(new ActivityDayDto(LocalDate.of(2026, 10, 1), ActivityDayType.ONSITE, false)));
        report.setDecidedAt(Instant.parse("2026-11-02T09:30:00Z"));
        when(activityReportService.findByFreelanceId(1L)).thenReturn(List.of(report));

        String json = new String(userDataExportService.exportUserData(1L), StandardCharsets.UTF_8);

        // Every section is present, and the profile and projects carry their data.
        assertThat(json)
            .contains("\"profile\"")
            .contains("John")
            .contains("john.doe@example.com")
            .contains("\"projects\"")
            .contains("Backend Dev")
            .contains("\"clients\"")
            .contains("\"contacts\"")
            .contains("\"sources\"")
            .contains("\"activityReports\"")
            .contains("VALIDATED")
            .contains("\"2026-10-01\"")
            .contains("\"2026-11-02T09:30:00Z\"");
    }
}
