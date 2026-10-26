package dev.swirlit.indezy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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

    // Self-contained mapper (the app does not expose an ObjectMapper bean); modules add date support, written as ISO-8601.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** The profile of account {@code userId} and the records of its workspace {@code freelanceId}. */
    @Transactional(readOnly = true)
    public byte[] exportUserData(Long userId, Long freelanceId) {
        log.debug("Exporting account data for user ID: {}", userId);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("profile", userService.getUserProfile(userId));
        export.put("projects", projectService.findByFreelanceId(freelanceId));
        export.put("clients", clientService.findByFreelanceId(freelanceId));
        export.put("contacts", contactService.findByFreelanceId(freelanceId));
        export.put("contactInteractions", contactInteractionService.export(freelanceId));
        export.put("contactRelations", contactRelationService.export(freelanceId));
        export.put("sources", sourceService.findByFreelanceId(freelanceId));
        export.put("activityReports", activityReportService.findByFreelanceId(freelanceId));
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

        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(export)
                .getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize user data export", e);
        }
    }
}
