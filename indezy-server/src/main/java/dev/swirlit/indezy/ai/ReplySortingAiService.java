package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.model.enums.ReplyKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
        only, not from the quoted email below it. For NOT_NOW, give in writeBackOn the date the answer says to write
        back (YYYY-MM-DD, after today; "in March" is the first of the next March), else leave it empty.""";

    private final AiService ai;

    /** The model's answer. */
    record Sorted(@JsonPropertyDescription("INTERESTED, NOT_NOW, NO_NEED, DO_NOT_CONTACT or OTHER") ReplyKind kind,
                  @JsonPropertyDescription("For NOT_NOW: the date to write back, YYYY-MM-DD; empty otherwise") String writeBackOn) {
    }

    /** What an answer says, and for a "not now" the date it gave to write back (null when none). */
    public record Sort(ReplyKind kind, LocalDate writeBackOn) {
    }

    /** What the answer says, read on {@code today}; OTHER when the AI gives nothing readable. */
    public Sort sort(Long userId, String subject, String text, LocalDate today) {
        String body = Objects.toString(text, "");
        String context = "<today>" + today + "</today>\n<subject>" + Objects.toString(subject, "") + "</subject>\n<answer>\n" +
            body.substring(0, Math.min(body.length(), TEXT_MAX)) + "\n</answer>";
        Sorted sorted = ai.structured(userId, AiFeature.TRIAGE, SYSTEM, context, Sorted.class, MAX_TOKENS, OutputConfig.Effort.LOW);
        if (sorted == null || sorted.kind() == null) {
            return new Sort(ReplyKind.OTHER, null);
        }
        return new Sort(sorted.kind(), sorted.kind() == ReplyKind.NOT_NOW ? date(sorted.writeBackOn(), today) : null);
    }

    /** The date given, kept only within two years after {@code today}. */
    private static LocalDate date(String value, LocalDate today) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value.strip());
            return date.isAfter(today) && !date.isAfter(today.plusYears(2)) ? date : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
