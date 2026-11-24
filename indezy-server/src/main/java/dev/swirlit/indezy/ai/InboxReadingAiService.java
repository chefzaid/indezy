package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.model.enums.InboxEmailKind;
import dev.swirlit.indezy.model.enums.LostReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

/**
 * Reads an email a freelancer forwarded to their inbox watcher (see docs/features.md#inbox-watcher): an interview
 * invite with the slots it offers, a rejection with its likely reason, or a question on their availability or rate,
 * and for an invite or a question the answer to send, from what Indezy knows of them; through the member's AI
 * allowance or key, on the message model.
 */
@Service
@RequiredArgsConstructor
public class InboxReadingAiService {

    static final int TEXT_MAX = 6_000;
    static final int SLOTS_MAX = 5;
    static final int ANSWER_MAX = 3_000;
    private static final long MAX_TOKENS = 1_500;
    private static final String SYSTEM = """
        You read an email a French IT freelancer received from a recruiter, an ESN or a client, which they forwarded to
        Indezy. Say what it is: INTERVIEW_INVITE (asks for an interview or a call about a mission), REJECTION (their
        application is not retained, the mission is filled or cancelled), AVAILABILITY_ASK (asks whether they are available
        or their daily rate, maybe for a mission), or OTHER (anything else, newsletters and offers sent to many included).
        Judge from the newest message, not from the quoted ones below it. For INTERVIEW_INVITE, list in slots the dates and
        times it proposes (YYYY-MM-DDTHH:MM, after today), none when it proposes none. For REJECTION, give the likely reason:
        RATE_TOO_LOW, POSITION_FILLED, PROFILE_MISMATCH, CLIENT_CANCELED or OTHER. For INTERVIEW_INVITE and
        AVAILABILITY_ASK, write in answer the short reply the freelancer would send, in the email's language, signed with
        their name: accept the first slot or offer the booking link when given, and answer an availability or rate
        question with what the profile says, never inventing a date or a rate it does not give. Otherwise leave answer
        empty.""";

    private final AiService ai;

    /** What the freelancer said of themselves, for the answer. */
    public record Profile(String name, LocalDate availableFrom, Integer targetDailyRate, String bookingLink) {
    }

    /** The model's answer. */
    record Reading(
        @JsonPropertyDescription("INTERVIEW_INVITE, REJECTION, AVAILABILITY_ASK or OTHER") InboxEmailKind kind,
        @JsonPropertyDescription("For INTERVIEW_INVITE: the proposed slots, YYYY-MM-DDTHH:MM; an empty list otherwise") List<String> slots,
        @JsonPropertyDescription("For REJECTION: RATE_TOO_LOW, POSITION_FILLED, PROFILE_MISMATCH, CLIENT_CANCELED or OTHER") LostReason reason,
        @JsonPropertyDescription("For INTERVIEW_INVITE and AVAILABILITY_ASK: the reply to send; empty otherwise") String answer) {

        Reading {
            slots = slots == null ? List.of() : List.copyOf(slots);
        }
    }

    /** What the email is, the slots it offers (within a year, at most five), the reason of a rejection and the answer. */
    public record Read(InboxEmailKind kind, List<LocalDateTime> slots, LostReason reason, String answer) {

        public Read {
            slots = List.copyOf(slots);
        }
    }

    public Read read(Long userId, String subject, String text, Profile profile, LocalDate today) {
        String body = Objects.toString(text, "");
        String context = "<today>" + today + "</today>\n<profile>\nName: " + Objects.toString(profile.name(), "") +
            "\nAvailable from: " + (profile.availableFrom() == null ? "not given" : profile.availableFrom()) +
            "\nDaily rate: " + (profile.targetDailyRate() == null ? "not given" : profile.targetDailyRate() + " EUR") +
            "\nBooking link: " + Objects.toString(profile.bookingLink(), "none") + "\n</profile>\n<subject>" +
            Objects.toString(subject, "") + "</subject>\n<email>\n" + body.substring(0, Math.min(body.length(), TEXT_MAX)) + "\n</email>";
        Reading reading = ai.structured(userId, AiFeature.MESSAGE, SYSTEM, context, Reading.class, MAX_TOKENS, OutputConfig.Effort.LOW);
        if (reading == null || reading.kind() == null || reading.kind() == InboxEmailKind.FORWARDING_CONFIRMATION) {
            return new Read(InboxEmailKind.OTHER, List.of(), null, null);
        }
        List<LocalDateTime> slots = reading.kind() == InboxEmailKind.INTERVIEW_INVITE ? slots(reading.slots(), today) : List.of();
        String answer = reading.kind() == InboxEmailKind.INTERVIEW_INVITE || reading.kind() == InboxEmailKind.AVAILABILITY_ASK ?
            Objects.toString(reading.answer(), "").strip() : "";
        return new Read(reading.kind(), slots, reading.kind() == InboxEmailKind.REJECTION ?
            Objects.requireNonNullElse(reading.reason(), LostReason.OTHER) : null,
            answer.isEmpty() ? null : answer.substring(0, Math.min(answer.length(), ANSWER_MAX)));
    }

    /** The readable slots after today and within a year, in order, five at most. */
    private static List<LocalDateTime> slots(List<String> values, LocalDate today) {
        return values.stream().filter(Objects::nonNull).map(value -> {
            try {
                return LocalDateTime.parse(value.strip());
            } catch (DateTimeParseException e) {
                return null;
            }
        }).filter(Objects::nonNull).filter(slot -> slot.toLocalDate().isAfter(today) && !slot.toLocalDate().isAfter(today.plusYears(1)))
            .sorted().distinct().limit(SLOTS_MAX).toList();
    }
}
