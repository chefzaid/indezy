package dev.swirlit.indezy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Assembles a full GDPR (droit à la portabilité) export of a user's account: their profile plus
 * every project, client, contact, source, activity report and job offer they acted on, serialised as pretty-printed JSON. Reuses
 * the existing DTO-returning services so the export stays in sync with the rest of the API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDataExportService {

    private final UserService userService;
    private final ProjectService projectService;
    private final ClientService clientService;
    private final ContactService contactService;
    private final ContactInteractionService contactInteractionService;
    private final ContactRelationService contactRelationService;
    private final SourceService sourceService;
    private final ActivityReportService activityReportService;
    private final OfferStateService offerStateService;
    private final SavedOfferSearchService savedOfferSearchService;
    private final OfferSearchSuggestionService offerSearchSuggestionService;
    private final OfferSiteHealthService offerSiteHealthService;
    private final OfferPublisherBlockService offerPublisherBlockService;
    private final PipelineTemplateService pipelineTemplateService;
    private final ProjectAttachmentService projectAttachmentService;
    private final EmailTemplateService emailTemplateService;
    private final ProjectNoteService projectNoteService;
    private final SeasonService seasonService;
    private final FreelanceService freelanceService;
    private final PublicProfileService publicProfileService;
    private final AccountSessionService accountSessionService;
    private final AccountEventService accountEventService;
    private final BillingIdentityService billingIdentityService;
    private final InvoiceService invoiceService;
    private final PurchaseOrderService purchaseOrderService;
    private final ComplianceDocumentService complianceDocumentService;
    private final CompliancePackService compliancePackService;
    private final BookingService bookingService;
    private final JsonMapper jsonMapper;

    /** The profile of account {@code userId} and the records of its workspace {@code freelanceId}. */
    @Transactional(readOnly = true)
    public byte[] exportUserData(Long userId, Long freelanceId) {
        log.debug("Exporting account data for user ID: {}", userId);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("profile", userService.getUserProfile(userId));
        export.put("publicProfile", publicProfileService.settings(userId));
        // Employment status, notice period, reversion and tax rates: the workspace's own record.
        export.put("freelance", freelanceService.findById(freelanceId));
        export.put("billing", billingIdentityService.get(freelanceId));
        export.put("invoices", invoiceService.list(freelanceId));
        export.put("projects", projectService.findByFreelanceId(freelanceId));
        export.put("projectNotes", projectNoteService.export(freelanceId));
        export.put("seasons", seasonService.findByFreelanceId(freelanceId));
        export.put("clients", clientService.findByFreelanceId(freelanceId));
        export.put("contacts", contactService.findByFreelanceId(freelanceId));
        export.put("contactInteractions", contactInteractionService.export(freelanceId));
        export.put("contactRelations", contactRelationService.export(freelanceId));
        export.put("sources", sourceService.findByFreelanceId(freelanceId));
        export.put("activityReports", activityReportService.findByFreelanceId(freelanceId));
        export.put("purchaseOrders", purchaseOrderService.export(freelanceId));
        export.put("complianceDocuments", complianceDocumentService.list(freelanceId));
        export.put("compliancePacks", compliancePackService.list(freelanceId));
        export.put("bookingPage", bookingService.page(freelanceId));
        export.put("bookings", bookingService.export(freelanceId));
        export.put("jobOffers", offerStateService.export(freelanceId));
        export.put("offerSearches", savedOfferSearchService.export(freelanceId));
        export.put("dismissedOfferSearchSuggestions", offerSearchSuggestionService.export(freelanceId));
        export.put("excludedOfferSites", offerSiteHealthService.excludedSites(freelanceId));
        export.put("offerSiteVisits", offerSiteHealthService.visits(freelanceId));
        export.put("blockedOfferPublishers", offerPublisherBlockService.list(freelanceId));
        export.put("pipelineTemplates", pipelineTemplateService.list(freelanceId));
        // The files themselves are downloaded from their opportunity's page.
        export.put("projectAttachments", projectAttachmentService.export(freelanceId));
        export.put("emailTemplates", emailTemplateService.list(freelanceId));
        export.put("sessions", accountSessionService.list(userId, null));
        export.put("accountActivity", accountEventService.export(userId));

        try {
            // Dates are ISO-8601 text, Jackson 3's default; each record's properties keep their declaration order, as
            // files exported before Jackson 3 have them, where Jackson 3 sorts them by name.
            return jsonMapper.rebuild().disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).build()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(export);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize user data export", e);
        }
    }
}
