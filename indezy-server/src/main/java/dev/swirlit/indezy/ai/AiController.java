package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.controller.PdfResponses;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.DossierFormat;
import dev.swirlit.indezy.model.enums.DossierLayout;
import dev.swirlit.indezy.model.enums.WorkMode;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
import java.util.Objects;

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
    private final ProspectionAiService prospection;
    private final NegotiationAiService negotiations;
    private final InterviewAiService interviews;
    private final CvRewriteAiService cvRewrites;
    private final ApplicationPackAiService packs;
    private final OfferBriefAiService briefs;
    private final SemanticSearchAiService semantic;
    private final ProfileImportAiService profileImports;
    private final AccessGuard accessGuard;
    private final AutopilotService autopilot;
    private final dev.swirlit.indezy.service.InboxWatcherService inboxWatcher;

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

    @Operation(summary = "The offers the daily autopilot run triaged in the last day, worth applying to first, and whether it is on")
    @GetMapping("/autopilot")
    public AutopilotService.Queue autopilot() {
        return autopilot.queue(SecurityUtils.getCurrentUserId(), accessGuard.requireWorkspace(null));
    }

    @Operation(summary = "Set aside an application the autopilot prepared")
    @DeleteMapping("/autopilot/packs/{id}")
    public ResponseEntity<Void> dismissPack(@PathVariable Long id) {
        autopilot.dismissPack(accessGuard.requireWorkspace(null), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set aside a follow-up the autopilot drafted")
    @DeleteMapping("/autopilot/drafts/{id}")
    public ResponseEntity<Void> dismissDraft(@PathVariable Long id) {
        autopilot.dismiss(accessGuard.requireWorkspace(null), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "The member's inbox watcher address and the emails forwarded to it lately, with what was done")
    @GetMapping("/inbox-watcher")
    public dev.swirlit.indezy.service.InboxWatcherService.Watcher inboxWatcher() {
        return inboxWatcher.watcher(SecurityUtils.getCurrentUserId(), accessGuard.requireWorkspace(null));
    }

    @Operation(summary = "Give the member a new inbox watcher address; the previous one receives nothing from then on")
    @PostMapping("/inbox-watcher/address")
    public dev.swirlit.indezy.service.InboxWatcherService.Watcher renewInboxAddress() {
        return inboxWatcher.renew(SecurityUtils.getCurrentUserId(), accessGuard.requireWorkspace(null));
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

    /** A question to search the workspace's opportunities and notes by. */
    public record SearchRequest(@jakarta.validation.constraints.NotBlank @Size(max = SemanticSearchAiService.QUERY_MAX) String q) {

        // Spring MVC logs bodies through toString at DEBUG: never the question.
        @Override
        public String toString() {
            return "SearchRequest[length=" + (q == null ? 0 : q.length()) + "]";
        }
    }

    @Operation(summary = "Search the workspace's opportunities and journal notes by meaning",
        description = "The latest 200 opportunities and 300 notes, shortened, read by the AI; ten at most, each with why it fits")
    @PostMapping("/search")
    public List<SemanticSearchAiService.Hit> search(@Valid @RequestBody SearchRequest request) {
        Long workspace = accessGuard.requireWorkspace(null);
        return semantic.search(SecurityUtils.getCurrentUserId(), workspace, request.q());
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

    /** The debrief of an interview, as the freelancer wrote it. */
    public record DebriefRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = InterviewAiService.DEBRIEF_MAX) String notes) {

        @Override
        public String toString() {
            return "DebriefRequest[" + (notes == null ? 0 : notes.length()) + " characters]";
        }
    }

    @Operation(summary = "Turn an interview's debrief into what comes next: its likely outcome, the next round, next steps and a thank-you message")
    @PostMapping("/interview/{projectId}/debrief")
    public InterviewAiService.Debrief debrief(@PathVariable Long projectId, @RequestParam(required = false) DocumentLanguage language,
                                              @Valid @RequestBody DebriefRequest request) {
        accessGuard.requireProject(projectId);
        return interviews.debrief(SecurityUtils.getCurrentUserId(), projectId, request.notes(), language == null ? DocumentLanguage.FR : language);
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

    @Operation(summary = "The dossier tailored to an opportunity, block by block before and after, to review before rendering it",
        description = "Render the blocks kept with POST /ai/cv/pdf")
    @PostMapping("/dossier/{projectId}/review")
    public CvRewriteAiService.CvRewrite reviewDossier(@PathVariable Long projectId, @RequestParam(defaultValue = "FR") DocumentLanguage language) {
        accessGuard.requireProject(projectId);
        Long freelanceId = accessGuard.requireWorkspace(null);
        return dossiers.reviewProject(SecurityUtils.getCurrentUserId(), freelanceId, projectId, language);
    }

    @Operation(summary = "The dossier tailored to a feed offer, block by block, the offer left out of the pipeline",
        description = "Render the blocks kept with POST /ai/cv/pdf")
    @PostMapping("/offers/{offerId}/dossier/review")
    public CvRewriteAiService.CvRewrite reviewOfferDossier(@PathVariable Long offerId, @RequestParam(defaultValue = "FR") DocumentLanguage language) {
        Long freelanceId = accessGuard.requireWorkspace(null);
        return dossiers.reviewOffer(SecurityUtils.getCurrentUserId(), freelanceId, offerId, language);
    }

    @Operation(summary = "The dossier tailored to a job posting pasted as text, block by block", description = "Nothing is kept")
    @PostMapping("/dossier/posting/review")
    public CvRewriteAiService.CvRewrite reviewPostingDossier(@RequestParam(defaultValue = "FR") DocumentLanguage language,
                                                             @RequestParam(defaultValue = "false") boolean fromFile,
                                                             @Valid @RequestBody PostingRequest request) {
        Long freelanceId = accessGuard.requireWorkspace(null);
        return dossiers.reviewPosting(SecurityUtils.getCurrentUserId(), freelanceId, request.text(), language, fromFile);
    }

    /** One block of the CV to refine, its locked phrases kept as written. */
    public record RefineRequest(@NotBlank @Size(max = CvRewriteAiService.REFINE_TEXT_MAX) String text,
                                @NotNull CvRewriteAiService.Refinement refinement, @Size(max = CvRewriteAiService.FOCUS_MAX) String focus,
                                @Size(max = CvRewriteAiService.LOCKED_MAX) List<@Size(max = CvRewriteAiService.LOCKED_LENGTH) String> locked,
                                @Size(max = CvRewriteAiService.ANSWER_MAX) String answer, DocumentLanguage language) {

        public RefineRequest {
            locked = locked == null ? List.of() : List.copyOf(locked.stream().filter(Objects::nonNull).toList());
        }
    }

    @Operation(summary = "Refine one block of the CV: shorter, more technical, plainer, translated, or bringing something out",
        description = "Refused with 409 when the AI changed a locked phrase")
    @PostMapping("/cv/refine")
    public CvRewriteAiService.Refined refineCv(@Valid @RequestBody RefineRequest request) {
        accessGuard.requireWorkspace(null);
        return new CvRewriteAiService.Refined(cvRewrites.refine(SecurityUtils.getCurrentUserId(), request.text(), request.refinement(),
            request.focus(), request.answer(), request.locked(), request.language() == null ? DocumentLanguage.FR : request.language()));
    }

    /** A block of the CV as it reads in the review. */
    public record QuestionBlock(@NotBlank @Size(max = CvRewriteAiService.BLOCK_KEY_MAX) String key,
                                @NotBlank @Size(max = CvRewriteAiService.REFINE_TEXT_MAX) String text) {
    }

    /** The blocks to ask impact questions on. */
    public record QuestionsRequest(@NotEmpty @Size(max = CvRewriteAiService.QUESTION_BLOCKS_MAX) List<@Valid @NotNull QuestionBlock> blocks,
                                   DocumentLanguage language) {

        public QuestionsRequest {
            blocks = blocks == null ? List.of() : List.copyOf(blocks.stream().filter(Objects::nonNull).toList());
        }
    }

    @Operation(summary = "Questions on the CV's vague blocks, for the freelancer to answer with facts they know",
        description = "At most five, one per block; the AI never suggests the answer. Weave an answer in with POST /ai/cv/refine (WEAVE)")
    @PostMapping("/cv/questions")
    public List<CvRewriteAiService.Question> cvQuestions(@Valid @RequestBody QuestionsRequest request) {
        accessGuard.requireWorkspace(null);
        return cvRewrites.questions(SecurityUtils.getCurrentUserId(), request.blocks().stream()
            .map(block -> new CvRewriteAiService.BlockText(block.key(), block.text())).toList(),
            request.language() == null ? DocumentLanguage.FR : request.language());
    }

    @Operation(summary = "The dossier de compétences tailored to a feed offer, as a PDF, without adding the offer to the pipeline",
        description = "Its summary, skill order and mission descriptions reworded for the offer; named or anonymized, in French or English")
    @PostMapping("/offers/{offerId}/dossier")
    public ResponseEntity<byte[]> offerDossier(@PathVariable Long offerId, @RequestParam(defaultValue = "NAMED") DossierLayout layout,
                                               @RequestParam(defaultValue = "FR") DocumentLanguage language) {
        Long freelanceId = accessGuard.requireWorkspace(null);
        return PdfResponses.attachment(dossiers.offerPdf(SecurityUtils.getCurrentUserId(), freelanceId, offerId, layout, language));
    }

    @Operation(summary = "The dossier de compétences tailored to a job posting pasted as text, as a PDF", description = "Nothing is kept")
    @PostMapping("/dossier/posting")
    public ResponseEntity<byte[]> postingDossier(@RequestParam(defaultValue = "NAMED") DossierLayout layout,
                                                 @RequestParam(defaultValue = "FR") DocumentLanguage language,
                                                 @Valid @RequestBody PostingRequest request) {
        Long freelanceId = accessGuard.requireWorkspace(null);
        return PdfResponses.attachment(dossiers.postingPdf(SecurityUtils.getCurrentUserId(), freelanceId, request.text(), layout, language));
    }

    /** What the whole CV is rewritten for: a positioning (a target role) and a style. */
    public record CvRewriteRequest(@Size(max = CvRewriteAiService.POSITIONING_MAX) String positioning, CvRewriteAiService.Style style,
                                   DocumentLanguage language, Boolean fromFile) {
    }

    @Operation(summary = "Rewrite the whole CV for a positioning and a style, tied to no opportunity",
        description = "The headline, summary, skill order, won missions' and past positions' descriptions, each before and after, to review")
    @PostMapping("/cv/rewrite")
    public CvRewriteAiService.CvRewrite rewriteCv(@Valid @RequestBody CvRewriteRequest request) {
        Long freelanceId = accessGuard.requireWorkspace(null);
        return cvRewrites.rewrite(SecurityUtils.getCurrentUserId(), freelanceId, request.positioning(), request.style(),
            request.language() == null ? DocumentLanguage.FR : request.language(), Boolean.TRUE.equals(request.fromFile()));
    }

    @Operation(summary = "The dossier de compétences with the reviewed rewriting, as a PDF or an editable Word document",
        description = "No AI is asked; named or anonymized; format=DOCX for a .docx file")
    @PostMapping("/cv/pdf")
    public ResponseEntity<byte[]> rewrittenCv(@RequestParam(defaultValue = "NAMED") DossierLayout layout,
                                              @RequestParam(defaultValue = "FR") DocumentLanguage language,
                                              @RequestParam(defaultValue = "PDF") DossierFormat format,
                                              @RequestBody CvRewriteAiService.Reviewed reviewed) {
        Long freelanceId = accessGuard.requireWorkspace(null);
        return PdfResponses.document(cvRewrites.file(SecurityUtils.getCurrentUserId(), freelanceId,
            new CvRewriteAiService.Rendering(layout, language, format), reviewed));
    }

    @Operation(summary = "Write the approved headline, summary and past positions' descriptions to the profile")
    @PutMapping("/cv/profile")
    public ResponseEntity<Void> approveCv(@RequestBody CvRewriteAiService.Approved approved) {
        Long freelanceId = accessGuard.requireWorkspace(null);
        cvRewrites.approve(SecurityUtils.getCurrentUserId(), freelanceId, approved);
        return ResponseEntity.noContent().build();
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

    /** The shared prospection message and the contacts to write each one for. */
    public record ProspectionRequest(@jakarta.validation.constraints.Size(max = 200) String subject,
                                     @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 20_000) String body,
                                     @jakarta.validation.constraints.NotEmpty
                                     @jakarta.validation.constraints.Size(max = ProspectionAiService.MAX_CONTACTS) List<Long> contactIds,
                                     DocumentLanguage language) {

        public ProspectionRequest {
            contactIds = contactIds == null ? null : List.copyOf(contactIds);
        }
    }

    @Operation(summary = "Write each ticked contact's own prospection email from the shared one; nothing sent",
        description = "20 contacts at most, among those the workspace may write to")
    @PostMapping("/prospection/messages")
    public List<ProspectionAiService.Written> prospection(@Valid @RequestBody ProspectionRequest request) {
        return prospection.write(SecurityUtils.getCurrentUserId(), accessGuard.requireWorkspace(null), request.subject(), request.body(),
            request.contactIds(), request.language() == null ? DocumentLanguage.FR : request.language());
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
