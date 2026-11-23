package dev.swirlit.indezy.ai;

import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.service.ContactEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Writes each prospection email for its contact (see docs/features.md#emails-sent-from-indezy): from the shared
 * availability message, one message per contact ticked, addressed by first name, speaking of their company, of the
 * newest mission it posted for the profile and of the last exchanges, the shared message's facts kept. Nothing is sent:
 * each becomes the contact's own message, read and changed with **Edit** before sending. One call for all of them.
 */
@Service
@RequiredArgsConstructor
public class ProspectionAiService {

    /** At most this many contacts are written to in one go. */
    public static final int MAX_CONTACTS = 20;
    static final int BODY_MAX = 4_000;
    private static final int EXCHANGES = 3;
    private static final int SUMMARY_MAX = 300;
    private static final long MAX_TOKENS = 8_000;
    private static final String SYSTEM = """
        You adapt the email a French freelance IT consultant sends ESN recruiters to say they will soon be available.
        For each contact given, write that contact's own version of the base message: address them by first name, keep
        every fact of the base message (the availability date, what the consultant does, the dossier attached, the
        signature), and make it personal from what is given about them only: their company, the newest mission their
        company posted for this profile, how often they answered and the last exchanges. 60 to 160 words, plain and
        warm, no subject line. Never invent missions, clients, figures or past exchanges. Answer one message per
        contact id given, in the language asked.""";

    private final AiService ai;
    private final ContactEmailService emails;
    private final ContactInteractionRepository interactions;

    /** A contact's message as written. */
    public record Written(Long contactId, String body) {
    }

    /** What the AI answers. */
    public record Answer(@JsonPropertyDescription("One message per contact id given") List<Item> messages) {

        public Answer {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }
    }

    public record Item(@JsonPropertyDescription("The contact id given") Long contactId,
                       @JsonPropertyDescription("The contact's own message, paragraphs separated by blank lines") String body) {
    }

    /** The contacts' own messages, for those of the ticked contacts the workspace may write to; others are left out. */
    @Transactional(readOnly = true)
    public List<Written> write(Long userId, Long freelanceId, String subject, String body, List<Long> contactIds, DocumentLanguage language) {
        Set<Long> asked = new HashSet<>(contactIds);
        List<ContactEmailService.Recipient> recipients = emails.availabilityRecipients(freelanceId).stream()
            .filter(recipient -> asked.contains(recipient.id())).limit(MAX_CONTACTS).toList();
        if (recipients.isEmpty()) {
            throw new IllegalArgumentException("None of these contacts can be written to");
        }
        Answer answer = ai.structured(userId, AiFeature.MESSAGE, SYSTEM, context(subject, body, recipients, language), Answer.class, MAX_TOKENS,
            OutputConfig.Effort.LOW);
        Set<Long> written = recipients.stream().map(ContactEmailService.Recipient::id).collect(java.util.stream.Collectors.toSet());
        List<Written> messages = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Item item : answer.messages()) {
            String text = Objects.toString(item.body(), "").strip();
            if (item.contactId() != null && written.contains(item.contactId()) && seen.add(item.contactId()) && !text.isEmpty()) {
                messages.add(new Written(item.contactId(), text.length() > BODY_MAX ? text.substring(0, BODY_MAX) : text));
            }
        }
        return messages;
    }

    String context(String subject, String body, List<ContactEmailService.Recipient> recipients, DocumentLanguage language) {
        StringBuilder text = new StringBuilder("Answer in ").append(language == DocumentLanguage.EN ? "English" : "French").append(".\n")
            .append("<base_message>\nSubject: ").append(Objects.toString(subject, "")).append("\n").append(Objects.toString(body, ""))
            .append("\n</base_message>\n");
        for (ContactEmailService.Recipient recipient : recipients) {
            text.append("<contact id=\"").append(recipient.id()).append("\">\n")
                .append("First name: ").append(Objects.toString(recipient.firstName(), "")).append("\n")
                .append("Company: ").append(Objects.toString(recipient.company(), "")).append("\n")
                .append("Times they answered or wrote: ").append(recipient.answers()).append("\n");
            if (recipient.mission() != null) {
                text.append("Newest mission their company posted for this profile: ").append(recipient.mission()).append("\n");
            }
            List<ContactInteraction> last = interactions.findByContactIdOrderByOccurredOnDescIdDesc(recipient.id()).stream()
                .limit(EXCHANGES).toList();
            for (ContactInteraction exchange : last) {
                String summary = Objects.toString(exchange.getSummary(), "").strip();
                text.append("Exchange on ").append(exchange.getOccurredOn()).append(exchange.getDirection() == InteractionDirection.INCOMING ?
                    " (from them): " : " (from the consultant): ").append(summary, 0, Math.min(summary.length(), SUMMARY_MAX)).append("\n");
            }
            text.append("</contact>\n");
        }
        return text.toString();
    }
}
