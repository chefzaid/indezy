package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.model.enums.ReplyKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Sorts an answer to an email sent from Indezy (see docs/features.md#emails-sent-from-indezy): interested, not now, no
 * need, do not contact, or anything else, from its subject and the start of its text, through the member's AI allowance
 * or key, on the triage model.
 */
@Service
@RequiredArgsConstructor
public class ReplySortingAiService {

    static final int TEXT_MAX = 3_000;
    private static final long MAX_TOKENS = 300;
    private static final String SYSTEM = """
        You sort the answer a French IT freelancer received to an email offering their services or following up on a
        mission. Say what the answer means: INTERESTED (a mission to discuss, a call or a resume asked for), NOT_NOW
        (nothing at the moment, maybe later), NO_NEED (no need for this profile), DO_NOT_CONTACT (asks not to be written
        to again, or to be removed), or OTHER (an out-of-office reply, a question, anything else). Judge from the answer
        only, not from the quoted email below it.""";

    private final AiService ai;

    /** The model's answer. */
    record Sorted(@JsonPropertyDescription("INTERESTED, NOT_NOW, NO_NEED, DO_NOT_CONTACT or OTHER") ReplyKind kind) {
    }

    /** What the answer says; OTHER when the AI gives nothing readable. */
    public ReplyKind sort(Long userId, String subject, String text) {
        String body = Objects.toString(text, "");
        String context = "<subject>" + Objects.toString(subject, "") + "</subject>\n<answer>\n" +
            body.substring(0, Math.min(body.length(), TEXT_MAX)) + "\n</answer>";
        Sorted sorted = ai.structured(userId, AiFeature.TRIAGE, SYSTEM, context, Sorted.class, MAX_TOKENS, OutputConfig.Effort.LOW);
        return sorted == null || sorted.kind() == null ? ReplyKind.OTHER : sorted.kind();
    }
}
