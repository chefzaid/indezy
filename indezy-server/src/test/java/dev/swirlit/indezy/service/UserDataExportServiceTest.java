package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ActivityDayDto;
import dev.swirlit.indezy.dto.ActivityReportDto;
import dev.swirlit.indezy.dto.OfferBoardVisitDto;
import dev.swirlit.indezy.dto.OfferPublisherBlockDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.OfferStateExportDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.dto.SavedOfferSearchDto;
import dev.swirlit.indezy.dto.UserDto;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import dev.swirlit.indezy.model.enums.OfferSite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

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

    @Mock
    private OfferStateService offerStateService;

    @Mock
    private SavedOfferSearchService savedOfferSearchService;

    @Mock
    private OfferSiteHealthService offerSiteHealthService;

    @Mock
    private OfferPublisherBlockService offerPublisherBlockService;

    @InjectMocks
    private UserDataExportService userDataExportService;

    @Test
    void exportUserData_ShouldSerializeTheProfileAndTheRecordsOfItsWorkspace() {
        UserDto profile = new UserDto();
        profile.setFirstName("John");
        profile.setEmail("john.doe@example.com");
        when(userService.getUserProfile(1L)).thenReturn(profile);

        ProjectDto project = new ProjectDto();
        project.setRole("Backend Dev");
        when(projectService.findByFreelanceId(5L)).thenReturn(List.of(project));
        when(clientService.findByFreelanceId(5L)).thenReturn(List.of());
        when(contactService.findByFreelanceId(5L)).thenReturn(List.of());
        when(sourceService.findByFreelanceId(5L)).thenReturn(List.of());
        ActivityReportDto report = new ActivityReportDto();
        report.setYear(2026);
        report.setMonth(10);
        report.setStatus(ActivityReportStatus.VALIDATED);
        report.setDays(List.of(new ActivityDayDto(LocalDate.of(2026, 10, 1), ActivityDayType.ONSITE, false)));
        report.setDecidedAt(Instant.parse("2026-11-02T09:30:00Z"));
        when(activityReportService.findByFreelanceId(5L)).thenReturn(List.of(report));
        when(offerStateService.export(5L)).thenReturn(List.of(new OfferStateExportDto(OfferSite.FREE_WORK, "Développeur Java",
            "Exemple Conseil", "https://www.free-work.com/7", true, false, Instant.parse("2026-10-19T08:00:00Z"), null, "Call Léa")));

        OfferSearchRequest filters = new OfferSearchRequest();
        filters.setQ("kafka");
        when(savedOfferSearchService.export(5L)).thenReturn(List.of(new SavedOfferSearchDto(3L, "Kafka à Paris", filters,
            Instant.parse("2026-10-18T07:00:00Z"), 0)));
        when(offerSiteHealthService.excludedSites(5L)).thenReturn(Set.of(OfferSite.YEETS));
        when(offerSiteHealthService.visits(5L)).thenReturn(List.of(new OfferBoardVisitDto(OfferLeftOutSite.MALT, Instant.parse("2026-10-20T08:00:00Z"))));
        when(offerPublisherBlockService.list(5L)).thenReturn(List.of(new OfferPublisherBlockDto(8L, "Exemple Conseil")));

        String json = new String(userDataExportService.exportUserData(1L, 5L), StandardCharsets.UTF_8);

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
            .contains("\"2026-11-02T09:30:00Z\"")
            .contains("\"jobOffers\"")
            .contains("FREE_WORK")
            .contains("\"2026-10-19T08:00:00Z\"")
            .contains("\"offerSearches\"")
            .contains("Kafka à Paris")
            .contains("\"q\" : \"kafka\"")
            .contains("\"excludedOfferSites\" : [ \"YEETS\" ]")
            .contains("\"blockedOfferPublishers\"")
            .contains("\"offerSiteVisits\"")
            .contains("Call Léa")
            .contains("\"MALT\"")
            .contains("\"name\" : \"Exemple Conseil\"");
    }
}
