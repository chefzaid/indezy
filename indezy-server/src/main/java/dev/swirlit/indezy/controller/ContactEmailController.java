package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ContactInteractionDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ContactEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/** Emails sent to contacts through Indezy, and the image that tells when a tracked one is opened. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Contact emails", description = "Emails to contacts sent through Indezy, with open tracking on request")
public class ContactEmailController {

    /** A transparent one-pixel GIF. */
    private static final byte[] PIXEL = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    private final ContactEmailService emails;
    private final AccessGuard accessGuard;

    /** An email to a contact. */
    public record SendRequest(Long projectId, @NotBlank @Size(max = ContactEmailService.SUBJECT_MAX) String subject,
                              @NotBlank @Size(max = ContactEmailService.BODY_MAX) String body, Boolean track, Boolean copy,
                              DocumentLanguage language) {

        // Spring MVC logs bodies through toString at DEBUG: never the subject or the message.
        @Override
        public String toString() {
            return "SendRequest[projectId=" + projectId + ", track=" + track + "]";
        }
    }

    @Operation(summary = "Whether email can go from Indezy, and how many went for the workspace in the last 24 hours")
    @GetMapping("/contacts/emails/status")
    public ContactEmailService.Status status() {
        return emails.status(accessGuard.requireWorkspace(null));
    }

    @Operation(summary = "Email a contact through Indezy", description = "Recorded as an exchange from the freelancer; tracked on request")
    @PostMapping("/contacts/{contactId}/emails")
    public ContactInteractionDto send(@PathVariable Long contactId, @Valid @RequestBody SendRequest request) {
        accessGuard.requireContact(contactId);
        accessGuard.requireProjectIfPresent(request.projectId());
        return emails.send(accessGuard.requireWorkspace(null), contactId, new ContactEmailService.Message(request.projectId(),
            request.subject(), request.body(), Boolean.TRUE.equals(request.track()), Boolean.TRUE.equals(request.copy()), request.language()));
    }

    @Operation(summary = "The tracking image of an email sent through Indezy", description = "Counts an open; the same image for any token")
    @GetMapping(value = "/public/emails/{token}.gif", produces = MediaType.IMAGE_GIF_VALUE)
    public ResponseEntity<byte[]> pixel(@PathVariable String token) {
        if (token.length() == 43 && token.matches("[A-Za-z0-9_-]+")) {
            emails.opened(token);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_GIF).body(PIXEL.clone());
    }
}
