package dev.swirlit.indezy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Assembles a full GDPR (droit à la portabilité) export of a user's account: their profile plus
 * every project, client, contact and source they own, serialised as pretty-printed JSON. Reuses
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
    private final SourceService sourceService;

    // Self-contained mapper (the app does not expose an ObjectMapper bean); modules add date support.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional(readOnly = true)
    public byte[] exportUserData(Long userId) {
        log.debug("Exporting account data for user ID: {}", userId);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("profile", userService.getUserProfile(userId));
        export.put("projects", projectService.findByFreelanceId(userId));
        export.put("clients", clientService.findByFreelanceId(userId));
        export.put("contacts", contactService.findByFreelanceId(userId));
        export.put("sources", sourceService.findByFreelanceId(userId));

        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(export)
                .getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize user data export", e);
        }
    }
}
