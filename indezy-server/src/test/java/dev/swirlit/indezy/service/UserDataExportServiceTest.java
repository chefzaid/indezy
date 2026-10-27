package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.AccountSecurityDtos;
import dev.swirlit.indezy.dto.ActivityDayDto;
import dev.swirlit.indezy.dto.ActivityReportDto;
import dev.swirlit.indezy.dto.FreelanceDto;
import dev.swirlit.indezy.dto.OfferBoardVisitDto;
import dev.swirlit.indezy.dto.OfferPublisherBlockDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.dto.OfferStateExportDto;
import dev.swirlit.indezy.dto.OfferSuggestionDismissalDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.dto.ProjectNoteDto;
import dev.swirlit.indezy.dto.PublicProfileSettingsDto;
import dev.swirlit.indezy.dto.SavedOfferSearchDto;
import dev.swirlit.indezy.dto.SavedSearchEmailsDto;
import dev.swirlit.indezy.dto.SeasonDto;
import dev.swirlit.indezy.dto.UserDto;
import dev.swirlit.indezy.model.OfferApplication;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.model.enums.ActivityDayType;
import dev.swirlit.indezy.model.enums.ActivityReportStatus;
import dev.swirlit.indezy.model.enums.ApplicationChannel;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.OfferLeftOutSite;
import dev.swirlit.indezy.model.enums.OfferSite;
import dev.swirlit.indezy.model.enums.SavedSearchEmails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private OfferSearchSuggestionService offerSearchSuggestionService;

    @Mock
    private OfferSiteHealthService offerSiteHealthService;

    @Mock
    private OfferPublisherBlockService offerPublisherBlockService;

    @Mock
    private PipelineTemplateService pipelineTemplateService;

    @Mock
    private ProjectAttachmentService projectAttachmentService;

    @Mock
    private EmailTemplateService emailTemplateService;

    @Mock
    private ContactInteractionService contactInteractionService;

    @Mock
    private ContactRelationService contactRelationService;

    @Mock
    private ProjectNoteService projectNoteService;

    @Mock
    private SeasonService seasonService;

    @Mock
    private FreelanceService freelanceService;

    @Mock
    private PublicProfileService publicProfileService;

    @Mock
    private AccountSessionService accountSessionService;

    @Mock
    private AccountEventService accountEventService;

    @Mock
    private BillingIdentityService billingIdentityService;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private PurchaseOrderService purchaseOrderService;

    @Mock
    private ComplianceDocumentService complianceDocumentService;

    @Mock
    private CompliancePackService compliancePackService;

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
        FreelanceDto freelance = new FreelanceDto();
        freelance.setNoticePeriodInDays(30);
        freelance.setReversionRate(0.55);
        when(freelanceService.findById(5L)).thenReturn(freelance);
        when(publicProfileService.settings(1L)).thenReturn(new PublicProfileSettingsDto(true, "alice-martin"));
        when(projectNoteService.export(5L)).thenReturn(List.of(new ProjectNoteDto(9L, 7L, "Called Léa about the rate",
            LocalDateTime.of(2026, 10, 21, 9, 30))));
        SeasonDto season = new SeasonDto();
        season.setName("Autumn 2026");
        when(seasonService.findByFreelanceId(5L)).thenReturn(List.of(season));
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
            "Exemple Conseil", "https://www.free-work.com/7", true, false, Instant.parse("2026-10-19T08:00:00Z"), null, "Call Léa",
            new OfferApplication(LocalDate.of(2026, 10, 19), ApplicationChannel.PHONE))));

        OfferSearchRequest filters = new OfferSearchRequest();
        filters.setQ("kafka");
        OfferSearchRequest dismissed = new OfferSearchRequest();
        dismissed.getSkills().add("Cobol");
        when(offerSearchSuggestionService.export(5L)).thenReturn(List.of(new OfferSuggestionDismissalDto(dismissed,
            Instant.parse("2026-10-20T07:00:00Z"))));
        when(savedOfferSearchService.export(5L)).thenReturn(List.of(new SavedOfferSearchDto(3L, "Kafka à Paris", filters,
            Instant.parse("2026-10-18T07:00:00Z"), 0, new SavedSearchEmailsDto(SavedSearchEmails.DAILY, 8, DocumentLanguage.FR, null), true)));
        when(offerSiteHealthService.excludedSites(5L)).thenReturn(Set.of(OfferSite.YEETS));
        when(offerSiteHealthService.visits(5L)).thenReturn(List.of(new OfferBoardVisitDto(OfferLeftOutSite.MALT, Instant.parse("2026-10-20T08:00:00Z"))));
        when(accountEventService.export(1L)).thenReturn(List.of(new AccountSecurityDtos.Event(3L, AccountEventType.PASSWORD_CHANGED, null,
            "Firefox on Linux", LocalDateTime.of(2026, 10, 22, 8, 0))));
        when(offerPublisherBlockService.list(5L)).thenReturn(List.of(new OfferPublisherBlockDto(8L, "Exemple Conseil")));

        String json = new String(userDataExportService.exportUserData(1L, 5L), StandardCharsets.UTF_8);

        // Every section is present, and the profile and projects carry their data.
        assertThat(json)
            .contains("\"profile\"")
            .contains("John")
            .contains("john.doe@example.com")
            .contains("\"projects\"")
            .contains("Backend Dev")
            .contains("\"freelance\"")
            .contains("\"publicProfile\"")
            .contains("\"sessions\"")
            .contains("\"accountActivity\"")
            .contains("PASSWORD_CHANGED")
            .contains("alice-martin")
            .contains("\"noticePeriodInDays\" : 30")
            .contains("\"reversionRate\" : 0.55")
            .contains("\"projectNotes\"")
            .contains("Called Léa about the rate")
            .contains("\"2026-10-21T09:30:00\"")
            .contains("\"seasons\"")
            .contains("Autumn 2026")
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
            .contains("\"dismissedOfferSearchSuggestions\"")
            .contains("Cobol")
            .contains("\"2026-10-20T07:00:00Z\"")
            .contains("\"q\" : \"kafka\"")
            .contains("\"excludedOfferSites\" : [ \"YEETS\" ]")
            .contains("\"blockedOfferPublishers\"")
            .contains("\"pipelineTemplates\"")
            .contains("\"projectAttachments\"")
            .contains("\"emailTemplates\"")
            .contains("\"offerSiteVisits\"")
            .contains("Call Léa")
            .contains("\"via\" : \"PHONE\"")
            .contains("\"MALT\"")
            .contains("\"name\" : \"Exemple Conseil\"");
    }
}
