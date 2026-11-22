package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
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

    @Operation(summary = "Go back to Indezy's key")
    @DeleteMapping("/key")
    public AiDtos.Status removeKey() {
        return ai.removeOwnKey(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Read a job posting into the fields of a new opportunity")
    @PostMapping("/posting")
    public PostingAiService.Posting posting(@Valid @RequestBody PostingRequest request) {
        accessGuard.requireWorkspace(null);
        return postings.read(SecurityUtils.getCurrentUserId(), request.text());
    }

    @Operation(summary = "Weigh an opportunity against the profile: fit, pros and cons, soft criteria, questions, rate")
    @PostMapping("/fit")
    public FitAiService.Fit fit(@Valid @RequestBody FitRequest request) {
        accessGuard.requireProject(request.projectId());
        return fits.weigh(SecurityUtils.getCurrentUserId(), request.projectId(),
            request.language() == null ? DocumentLanguage.FR : request.language(), request.priorities());
    }

    @Operation(summary = "Draft a message about an opportunity: an application, a follow-up, a thank-you or a decline")
    @PostMapping("/message")
    public MessageAiService.Draft message(@Valid @RequestBody MessageRequest request) {
        accessGuard.requireProject(request.projectId());
        return messages.draft(SecurityUtils.getCurrentUserId(), request.projectId(), request.kind(),
            request.language() == null ? DocumentLanguage.FR : request.language(), request.notes());
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
