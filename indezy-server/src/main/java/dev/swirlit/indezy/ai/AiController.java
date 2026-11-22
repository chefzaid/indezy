package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.controller.PdfResponses;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierLayout;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/** The AI features: whether they can be used, the member's own key, and each feature. */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Tag(name = "AI", description = "Features using Claude, through Indezy's key within its limits or the member's own")
public class AiController {

    private final AiService ai;
    private final PostingAiService postings;
    private final MessageAiService messages;
    private final FitAiService fits;
    private final ChatAiService chats;
    private final OfferTriageAiService triage;
    private final DossierAiService dossiers;
    private final RateAiService rates;
    private final AvailabilityAiService availability;
    private final NegotiationAiService negotiations;
    private final InterviewAiService interviews;
    private final ApplicationPackAiService packs;
    private final OfferBriefAiService briefs;
    private final ProfileImportAiService profileImports;
    private final AccessGuard accessGuard;

    /** A job posting pasted as text. */
    public record PostingRequest(@NotBlank @Size(max = PostingAiService.TEXT_MAX) String text) {

        @Override
        public String toString() {
            return "PostingRequest[]";
        }
    }

    /** A message to draft about one of the workspace's opportunities. */
    public record MessageRequest(@NotNull Long projectId, @NotNull MessageAiService.Kind kind, DocumentLanguage language,
                                 @Size(max = MessageAiService.NOTES_MAX) String notes) {

        @Override
        public String toString() {
            return "MessageRequest[projectId=" + projectId + ", kind=" + kind + "]";
        }
    }

    /** An opportunity to weigh against the profile, with what matters to the freelancer. */
    public record FitRequest(@NotNull Long projectId, DocumentLanguage language, @Size(max = FitAiService.PRIORITIES_MAX) String priorities) {

        @Override
        public String toString() {
            return "FitRequest[projectId=" + projectId + "]";
        }
    }

    /** A question about an opportunity. */
    public record Question(@NotBlank @Size(max = ChatAiService.QUESTION_MAX) String text) {

        @Override
        public String toString() {
            return "Question[]";
        }
    }

    @Operation(summary = "Whether AI can be used, through whose key, and this month's allowance")
    @GetMapping("/status")
    public AiDtos.Status status() {
        return ai.status(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Use the member's own Anthropic key, without Indezy's limits")
    @PutMapping("/key")
    public AiDtos.Status setKey(@Valid @RequestBody AiDtos.OwnKey key) {
        return ai.setOwnKey(SecurityUtils.getCurrentUserId(), key);
    }

    @Operation(summary = "Choose the model of some tasks for the member's own key", description = "Tasks left out keep the key's model or their own")
    @PutMapping("/key/models")
    public AiDtos.Status setTaskModels(@Valid @RequestBody AiDtos.TaskModels models) {
        return ai.setTaskModels(SecurityUtils.getCurrentUserId(), models);
    }

    @Operation(summary = "Go back to Indezy's key")
    @DeleteMapping("/key")
    public AiDtos.Status removeKey() {
        return ai.removeOwnKey(SecurityUtils.getCurrentUserId());
    }

    /** What the freelancer looks for, in their words. */
    public record BriefRequest(@NotBlank @Size(max = OfferBriefAiService.TEXT_MAX) String text) {

        // Spring MVC logs bodies through toString at DEBUG: never the text.
        @Override
        public String toString() {
            return "BriefRequest[length=" + (text == null ? 0 : text.length()) + "]";
        }
    }

    @Operation(summary = "Read what the freelancer looks for into the offer feed's filters")
    @PostMapping("/offer-brief")
    public OfferBriefAiService.Brief offerBrief(@Valid @RequestBody BriefRequest request) {
        accessGuard.requireWorkspace(null);
        return briefs.read(SecurityUtils.getCurrentUserId(), request.text());
    }

    @Operation(summary = "Read a job posting into the fields of a new opportunity")
    @PostMapping("/posting")
    public PostingAiService.Posting posting(@Valid @RequestBody PostingRequest request) {
        accessGuard.requireWorkspace(null);
        return postings.read(SecurityUtils.getCurrentUserId(), request.text());
    }

    @Operation(summary = "Read a CV (PDF or docx) into the profile's fields, to pick from; nothing is kept")
    @PostMapping(value = "/profile-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileImportAiService.Reading profileImport(@RequestParam("file") MultipartFile file) throws IOException {
        accessGuard.requireWorkspace(null);
        return profileImports.read(SecurityUtils.getCurrentUserId(), file.getOriginalFilename(), file.getBytes());
    }

    @Operation(summary = "The text of a job posting saved as a PDF, to check before reading it", description = "No AI: 5 MB and 10 pages at most")
    @PostMapping(value = "/posting/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PostingAiService.PdfText postingPdf(@RequestParam("file") MultipartFile file) throws IOException {
        accessGuard.requireWorkspace(null);
        return PostingAiService.pdfText(file.getBytes());
    }

    @Operation(summary = "Weigh an opportunity against the profile: fit, pros and cons, soft criteria, questions, rate")
    @PostMapping("/fit")
    public FitAiService.Fit fit(@Valid @RequestBody FitRequest request) {
        accessGuard.requireProject(request.projectId());
        return fits.weigh(SecurityUtils.getCurrentUserId(), request.projectId(),
            request.language() == null ? DocumentLanguage.FR : request.language(), request.priorities());
    }

    @Operation(summary = "Prepare the rate negotiation of an opportunity: counter-offer, walk-away rate, the ESN's margin, arguments")
    @PostMapping("/negotiation/{projectId}")
    public NegotiationAiService.Preparation negotiation(@PathVariable Long projectId, @RequestParam(required = false) DocumentLanguage language) {
        accessGuard.requireProject(projectId);
        return negotiations.prepare(SecurityUtils.getCurrentUserId(), projectId, language == null ? DocumentLanguage.FR : language);
    }

    @Operation(summary = "Prepare the next interview of an opportunity: likely questions with hints, a two-minute pitch, questions to ask")
    @PostMapping("/interview/{projectId}")
    public InterviewAiService.Preparation interview(@PathVariable Long projectId, @RequestParam(required = false) DocumentLanguage language) {
        accessGuard.requireProject(projectId);
        return interviews.prepare(SecurityUtils.getCurrentUserId(), projectId, language == null ? DocumentLanguage.FR : language);
    }

    @Operation(summary = "Draft a message about an opportunity: an application, a follow-up, a thank-you or a decline")
    @PostMapping("/message")
    public MessageAiService.Draft message(@Valid @RequestBody MessageRequest request) {
        accessGuard.requireProject(request.projectId());
        return messages.draft(SecurityUtils.getCurrentUserId(), request.projectId(), request.kind(),
            request.language() == null ? DocumentLanguage.FR : request.language(), request.notes());
    }

    /** A message to polish. */
    public record PolishRequest(@Size(max = MessageAiService.POLISH_SUBJECT_MAX) String subject,
                                @NotBlank @Size(max = MessageAiService.POLISH_BODY_MAX) String body, MessageAiService.Tone tone) {

        // Spring MVC logs bodies through toString at DEBUG: never the message.
        @Override
        public String toString() {
            return "PolishRequest[tone=" + tone + "]";
        }
    }

    @Operation(summary = "Polish a message before sending it: spelling and grammar, and the tone asked, its variables kept")
    @PostMapping("/polish")
    public MessageAiService.Draft polish(@Valid @RequestBody PolishRequest request) {
        accessGuard.requireWorkspace(null);
        return messages.polish(SecurityUtils.getCurrentUserId(), request.subject(), request.body(),
            request.tone() == null ? MessageAiService.Tone.KEEP : request.tone());
    }

    @Operation(summary = "The AI conversation about an opportunity, oldest first")
    @GetMapping("/chat/{projectId}")
    public List<ChatAiService.Turn> chat(@PathVariable Long projectId) {
        accessGuard.requireProject(projectId);
        return chats.history(projectId);
    }

    @Operation(summary = "Ask the AI about an opportunity; the question and the answer are kept with it")
    @PostMapping("/chat/{projectId}")
    public List<ChatAiService.Turn> ask(@PathVariable Long projectId, @Valid @RequestBody Question question) {
        accessGuard.requireProject(projectId);
        return chats.ask(SecurityUtils.getCurrentUserId(), projectId, question.text());
    }

    @Operation(summary = "Clear the AI conversation about an opportunity")
    @DeleteMapping("/chat/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearChat(@PathVariable Long projectId) {
        accessGuard.requireProject(projectId);
        chats.clear(projectId);
    }

    @Operation(summary = "The dossier de compétences tailored to an opportunity, as a PDF",
        description = "Its summary, skill order and mission descriptions reworded for the opportunity; named or anonymized, in French or English")
    @PostMapping("/dossier/{projectId}")
    public ResponseEntity<byte[]> dossier(@PathVariable Long projectId, @RequestParam(defaultValue = "NAMED") DossierLayout layout,
                                          @RequestParam(defaultValue = "FR") DocumentLanguage language) {
        accessGuard.requireProject(projectId);
        Long freelanceId = accessGuard.requireWorkspace(null);
        return PdfResponses.attachment(dossiers.pdf(SecurityUtils.getCurrentUserId(), freelanceId, projectId, layout, language));
    }

    /** An opportunity being written, to suggest a rate for. */
    public record RateRequest(@Size(max = 300) String role, @Size(max = 1_000) String techStack, WorkMode workMode, Integer remoteDaysPerMonth,
                              @Size(max = 300) String place, Integer durationInMonths, @Size(max = RateAiService.TEXT_MAX) String description,
                              DocumentLanguage language) {

        // Spring MVC logs bodies through toString at DEBUG: never the description.
        @Override
        public String toString() {
            return "RateRequest[workMode=" + workMode + "]";
        }
    }

    @Operation(summary = "Suggest the daily rate to ask for an opportunity being written, from the profile and the market")
    @PostMapping("/rate")
    public RateAiService.Suggestion rate(@Valid @RequestBody RateRequest request) {
        accessGuard.requireWorkspace(null);
        return rates.suggest(SecurityUtils.getCurrentUserId(), new RateAiService.Mission(request.role(), request.techStack(), request.workMode(),
            request.remoteDaysPerMonth(), request.place(), request.durationInMonths(), request.description()),
            request.language() == null ? DocumentLanguage.FR : request.language());
    }

    @Operation(summary = "Draft the announcement of the next availability: a LinkedIn post and a network post, nothing posted")
    @PostMapping("/availability")
    public AvailabilityAiService.Announcement availability(@RequestParam(required = false) DocumentLanguage language) {
        return availability.draft(SecurityUtils.getCurrentUserId(), accessGuard.requireWorkspace(null),
            language == null ? DocumentLanguage.FR : language);
    }

    @Operation(summary = "Prepare the application to a feed offer: the channel's message, screening answers and a rate to propose")
    @PostMapping("/offers/{offerId}/pack")
    public ApplicationPackAiService.Draft pack(@PathVariable Long offerId,
                                               @RequestParam(defaultValue = "ESN_EMAIL") ApplicationPackAiService.Channel channel,
                                               @RequestParam(required = false) DocumentLanguage language) {
        return packs.pack(SecurityUtils.getCurrentUserId(), accessGuard.requireWorkspace(null), offerId, channel,
            language == null ? DocumentLanguage.FR : language);
    }

    @Operation(summary = "The AI verdict kept on a feed offer for the workspace", description = "204 when none was asked yet")
    @GetMapping("/offers/{offerId}/verdict")
    public ResponseEntity<OfferTriageAiService.Verdict> verdict(@PathVariable Long offerId) {
        return triage.stored(accessGuard.requireWorkspace(null), offerId).map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Ask the AI whether a feed offer is worth applying to, and its deal-breakers")
    @PostMapping("/offers/{offerId}/verdict")
    public OfferTriageAiService.Verdict triage(@PathVariable Long offerId, @RequestParam(required = false) DocumentLanguage language) {
        return triage.triage(SecurityUtils.getCurrentUserId(), accessGuard.requireWorkspace(null), offerId,
            language == null ? DocumentLanguage.FR : language);
    }
}
