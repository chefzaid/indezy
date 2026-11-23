package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ContactInteractionDto;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ContactEmailScheduleService;
import dev.swirlit.indezy.service.ContactEmailService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import dev.swirlit.indezy.util.SecurityUtils;
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
import java.util.List;

/** Emails sent to contacts through Indezy, and the image that tells when a tracked one is opened. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Contact emails", description = "Emails to contacts sent through Indezy, with open tracking on request")
public class ContactEmailController {

    /** A transparent one-pixel GIF. */
    private static final byte[] PIXEL = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    private final ContactEmailService emails;
    private final ContactEmailScheduleService schedule;
    private final dev.swirlit.indezy.service.ProspectionCampaignService campaigns;
    private final AccessGuard accessGuard;

    /** An email to a contact. */
    public record SendRequest(Long projectId, @NotBlank @Size(max = ContactEmailService.SUBJECT_MAX) String subject,
                              @NotBlank @Size(max = ContactEmailService.BODY_MAX) String body, Boolean track, Boolean copy,
                              DocumentLanguage language, Boolean dossier, Long campaignId) {

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
        Long workspace = accessGuard.requireWorkspace(null);
        Long campaign = request.campaignId() == null ? null : campaigns.require(workspace, request.campaignId());
        return emails.send(SecurityUtils.getCurrentUserId(), workspace, contactId, new ContactEmailService.Message(
            request.projectId(), request.subject(), request.body(), Boolean.TRUE.equals(request.track()), Boolean.TRUE.equals(request.copy()),
            request.language(), Boolean.TRUE.equals(request.dossier()), campaign));
    }

    /** A campaign to start: what it is called (the emails' subject). */
    public record CampaignRequest(@Size(max = dev.swirlit.indezy.model.ProspectionCampaign.NAME_MAX) String name) {
    }

    @Operation(summary = "Start a prospection campaign, its emails then sent or planned with its id")
    @PostMapping("/contacts/emails/campaigns")
    public dev.swirlit.indezy.service.ProspectionCampaignService.Campaign startCampaign(@Valid @RequestBody CampaignRequest request) {
        return campaigns.start(accessGuard.requireWorkspace(null), request.name());
    }

    @Operation(summary = "The workspace's latest campaigns and what came of them",
        description = "Emails sent and waiting, contacts who answered since, opportunities opened since with their companies, missions won")
    @GetMapping("/contacts/emails/campaigns")
    public List<dev.swirlit.indezy.service.ProspectionCampaignService.Campaign> campaigns() {
        return campaigns.list(accessGuard.requireWorkspace(null));
    }

    @Operation(summary = "The contacts of the ESNs the workspace worked with, who have an address",
        description = "Whom to tell of an availability, by company then name")
    @GetMapping("/contacts/emails/availability-recipients")
    public List<ContactEmailService.Recipient> availabilityRecipients() {
        return emails.availabilityRecipients(accessGuard.requireWorkspace(null));
    }

    /** One email of a plan, written for its contact. */
    public record ScheduledDraft(@NotNull Long contactId, @NotBlank @Size(max = ContactEmailService.SUBJECT_MAX) String subject,
                                 @NotBlank @Size(max = ContactEmailService.BODY_MAX) String body) {

        // Spring MVC logs bodies through toString at DEBUG: never the subject or the message.
        @Override
        public String toString() {
            return "ScheduledDraft[contactId=" + contactId + "]";
        }
    }

    /** Emails to send later, spread over working days. */
    public record ScheduleRequest(@NotEmpty @Size(max = 100) List<@Valid @NotNull ScheduledDraft> emails, Boolean dossier, DocumentLanguage language,
                                  @Min(1) @Max(10) int days, @Min(1) @Max(ContactEmailService.DAILY_MAX) int dailyMax, Long campaignId) {

        public ScheduleRequest {
            emails = emails == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(emails));
        }
    }

    @Operation(summary = "Send emails to contacts later, spread over working days",
        description = "Monday to Friday but public holidays, 9:00 to 17:00 in Paris, at most dailyMax a day; each recorded when it goes")
    @PostMapping("/contacts/emails/scheduled")
    public List<ContactEmailScheduleService.Waiting> schedule(@Valid @RequestBody ScheduleRequest request) {
        request.emails().forEach(email -> accessGuard.requireContact(email.contactId()));
        Long workspace = accessGuard.requireWorkspace(null);
        Long campaign = request.campaignId() == null ? null : campaigns.require(workspace, request.campaignId());
        return schedule.schedule(SecurityUtils.getCurrentUserId(), workspace, new ContactEmailScheduleService.Plan(
            request.emails().stream().map(email -> new ContactEmailScheduleService.Draft(email.contactId(), email.subject(), email.body())).toList(),
            Boolean.TRUE.equals(request.dossier()), request.language(), request.days(), request.dailyMax(), campaign));
    }

    /** A campaign's reminders, one per contact, written for each. */
    public record ReminderRequest(@NotNull Long campaignId, @NotEmpty @Size(max = 100) List<@Valid @NotNull ScheduledDraft> emails,
                                  DocumentLanguage language) {

        public ReminderRequest {
            emails = emails == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(emails));
        }
    }

    @Operation(summary = "Remind a campaign's contacts who did not answer, 5 working days after its first email to each",
        description = "Each goes only if the first email went and the contact was not in touch otherwise since; cancelled like any waiting email")
    @PostMapping("/contacts/emails/reminders")
    public List<ContactEmailScheduleService.Waiting> remind(@Valid @RequestBody ReminderRequest request) {
        request.emails().forEach(email -> accessGuard.requireContact(email.contactId()));
        Long workspace = accessGuard.requireWorkspace(null);
        Long campaign = campaigns.require(workspace, request.campaignId());
        return schedule.remind(SecurityUtils.getCurrentUserId(), workspace, campaign,
            request.emails().stream().map(email -> new ContactEmailScheduleService.Draft(email.contactId(), email.subject(), email.body())).toList(),
            request.language() == null ? DocumentLanguage.FR : request.language());
    }

    @Operation(summary = "The workspace's emails waiting to go, the soonest first")
    @GetMapping("/contacts/emails/scheduled")
    public List<ContactEmailScheduleService.Waiting> waiting() {
        return schedule.waiting(accessGuard.requireWorkspace(null));
    }

    @Operation(summary = "Cancel an email waiting to go")
    @DeleteMapping("/contacts/emails/scheduled/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        schedule.cancel(accessGuard.requireWorkspace(null), id);
    }

    @Operation(summary = "The page of a prospecting email's objection link, asking to confirm", description = "Changes nothing by itself")
    @GetMapping(value = "/public/emails/object/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> objectionPage(@PathVariable String token) {
        java.util.Optional<ContactEmailService.Objection> objection = validToken(token) ? emails.objection(token) : java.util.Optional.empty();
        if (objection.isEmpty()) {
            return objectionAnswer(HttpStatus.NOT_FOUND, "Lien inconnu", "Ce lien n'existe pas ou plus. / This link does not exist.", null);
        }
        String sender = ContactEmailPages.escape(objection.get().senderName());
        return objectionAnswer(HttpStatus.OK, "Ne plus être contacté", "Ne plus recevoir d'e-mail de " + sender + " ? / Stop receiving emails from " +
            sender + "?", token);
    }

    @Operation(summary = "Object to being contacted again through a prospecting email's link",
        description = "Marks the contact as asking not to be contacted; the same answer when objected already")
    @org.springframework.web.bind.annotation.PostMapping(value = "/public/emails/object/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> object(@PathVariable String token) {
        if (!validToken(token) || !emails.object(token)) {
            return objectionAnswer(HttpStatus.NOT_FOUND, "Lien inconnu", "Ce lien n'existe pas ou plus. / This link does not exist.", null);
        }
        return objectionAnswer(HttpStatus.OK, "C'est noté", "Vous ne serez plus contacté. / You will not be contacted again.", null);
    }

    private static boolean validToken(String token) {
        return token.length() == 43 && token.matches("[A-Za-z0-9_-]+");
    }

    private static ResponseEntity<String> objectionAnswer(HttpStatus status, String title, String message, String formToken) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML)
            .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'")
            .header("X-Robots-Tag", "noindex").body(ContactEmailPages.page(title, message, formToken));
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
