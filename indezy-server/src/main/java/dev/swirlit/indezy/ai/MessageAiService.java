package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Drafts a message about an opportunity, for the freelancer to read, change and send themselves: an application, a
 * follow-up, a thank-you after an interview or a polite decline. Built from the opportunity alone: the role, the company,
 * the stack, its stage and the freelancer's notes on what to say.
 */
@Service
@RequiredArgsConstructor
public class MessageAiService {

    static final int NOTES_MAX = 1_000;
    private static final int DESCRIPTION_MAX = 4_000;
    private static final long MAX_TOKENS = 2_000;
    private static final String SYSTEM = """
        You help a French freelance IT consultant write short professional messages about a mission: to an ESN recruiter,
        an account manager or a client. Write plainly and warmly, without flattery, in the language asked, in the
        freelancer's name, with no placeholder to fill in except where a fact is missing. Never invent experience, rates,
        dates or names the context does not give. Keep it under 150 words.""";

    static final int POLISH_SUBJECT_MAX = 200;
    static final int POLISH_BODY_MAX = 5_000;
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*\\w+\\s*\\}\\}");
    private static final String POLISH_SYSTEM = """
        You polish a message a French freelance IT consultant is about to send: fix spelling, grammar and punctuation, and
        adjust the tone as asked, in the message's own language. Keep its meaning, facts, names, figures, dates and links,
        add nothing it does not say, and keep every {{variable}} exactly as written. Keep its greeting and sign-off.""";

    /** How to polish a message. */
    public enum Tone {
        /** Spelling and grammar only. */
        KEEP,
        /** More formal, as to a client's management. */
        FORMAL,
        /** Warmer, as to a recruiter one knows. */
        FRIENDLY,
        /** Shorter, the same points. */
        SHORTER
    }

    private static String tone(Tone tone) {
        return switch (tone) {
            case KEEP -> "Fix spelling, grammar and punctuation only; keep the wording otherwise.";
            case FORMAL -> "Make it more formal and polished, as to a client's management.";
            case FRIENDLY -> "Make it warmer and more natural, as to a recruiter one knows, still professional.";
            case SHORTER -> "Make it shorter and more direct, keeping every point.";
        };
    }

    /** {@code subject} and {@code body} polished, their variables kept; refused when the AI dropped or added one. */
    public Draft polish(Long userId, String subject, String body, Tone tone) {
        String context = tone(tone) + "\n<subject>\n" + Objects.toString(subject, "").strip() + "\n</subject>\n<message>\n" + body.strip() +
            "\n</message>\n";
        Draft draft = ai.structured(userId, AiFeature.MESSAGE, POLISH_SYSTEM, context, Draft.class, MAX_TOKENS, OutputConfig.Effort.LOW);
        Draft polished = new Draft(Objects.toString(draft.subject(), "").strip(), Objects.toString(draft.body(), "").strip());
        if (!variables(Objects.toString(subject, "") + " " + body).equals(variables(polished.subject() + " " + polished.body())) ||
            polished.body().isEmpty()) {
            throw new InvalidStateException("The AI changed the message's variables: polish it again or edit it by hand");
        }
        return polished;
    }

    /** The {@code {{variables}}} of a text, as written without spaces, sorted. */
    static List<String> variables(String text) {
        return VARIABLE.matcher(text).results().map(match -> match.group().replaceAll("\\s", "")).sorted().toList();
    }

    /** What the message is for. */
    public enum Kind {
        APPLICATION, FOLLOW_UP, THANK_YOU, DECLINE
    }

    private final AiService ai;
    private final ProjectRepository projects;

    /** A message drafted: its subject line, for an email, and its text. */
    public record Draft(
        @JsonPropertyDescription("A short email subject line") String subject,
        @JsonPropertyDescription("The message, with its greeting and sign-off, paragraphs separated by blank lines") String body) {
    }

    private static String ask(Kind kind) {
        return switch (kind) {
            case APPLICATION -> "an application for this mission";
            case FOLLOW_UP -> "a follow-up asking where the process stands";
            case THANK_YOU -> "a thank-you after the latest interview, saying the freelancer is still interested";
            case DECLINE -> "a polite message declining this mission, keeping the relationship";
        };
    }

    @Transactional(readOnly = true)
    public Draft draft(Long userId, Long projectId, Kind kind, DocumentLanguage language, String notes) {
        Project project = projects.findById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        Freelance freelance = project.getFreelance();
        String description = Objects.toString(project.getDescription(), "");
        StringBuilder context = new StringBuilder()
            .append("Write ").append(ask(kind))
            .append(", in ").append(language == DocumentLanguage.EN ? "English" : "French").append(".\n<mission>\n")
            .append("Role: ").append(Objects.toString(project.getRole(), "")).append('\n')
            .append("Company: ").append(project.getMiddleman() != null ? project.getMiddleman().getCompanyName() :
                project.getClient() != null ? project.getClient().getCompanyName() : "").append('\n')
            .append("Stack: ").append(Objects.toString(project.getTechStack(), "")).append('\n')
            .append("Stage: ").append(project.getStatus()).append('\n')
            .append("Description: ").append(description, 0, Math.min(description.length(), DESCRIPTION_MAX)).append("\n</mission>\n")
            .append("Freelancer: ").append(freelance == null ? "" : freelance.getFirstName() + " " + freelance.getLastName()).append('\n');
        if (notes != null && !notes.isBlank()) {
            context.append("<what to say>\n").append(notes.strip(), 0, Math.min(notes.strip().length(), NOTES_MAX)).append("\n</what to say>\n");
        }
        Draft draft = ai.structured(userId, AiFeature.MESSAGE, SYSTEM, context.toString(), Draft.class, MAX_TOKENS, OutputConfig.Effort.LOW);
        return new Draft(Objects.toString(draft.subject(), "").strip(), Objects.toString(draft.body(), "").strip());
    }
}
