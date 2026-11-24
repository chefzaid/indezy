package dev.swirlit.indezy.mail;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static dev.swirlit.indezy.mail.SavedSearchEmailComposer.escape;

/**
 * Forwards to a member the answer a contact sent to the reply address of an email sent from Indezy (see
 * docs/features.md#emails-sent-from-indezy): who answered and to what, the answer's text, the exchange marked
 * answered; replying to it writes to the contact directly.
 */
@Component
public class ReplyForwardComposer {

    private static final String MUTED = "#6B7280";
    private static final int TEXT_MAX = 20_000;

    /** The answer: to whom it goes, who sent it (their address for replies), the email it answers and its text. */
    public record Answer(String to, String contactName, String contactAddress, String subject, String text, DocumentLanguage language,
                         String key) {

        @Override
        public String toString() {
            return "Answer[" + key + "]";
        }
    }

    public Email compose(Answer answer) {
        boolean english = answer.language() == DocumentLanguage.EN;
        String subject = answer.subject() == null || answer.subject().isBlank() ? (english ? "An answer from " : "Une réponse de ") +
            answer.contactName() : answer.subject().strip();
        String note = english ?
            answer.contactName() + " answered your email sent from Indezy; the exchange is marked answered. Reply to this email to write to them." :
            answer.contactName() + " a répondu à votre e-mail envoyé depuis Indezy ; l'échange est marqué comme répondu. Répondez à cet e-mail pour lui écrire.";
        String text = answer.text() == null ? "" : answer.text().length() > TEXT_MAX ? answer.text().substring(0, TEXT_MAX) + "…" : answer.text();
        String html = "<p style=\"color:" + MUTED + "\">" + escape(note) + "</p><div style=\"white-space:pre-line\">" + escape(text) + "</div>";
        String plain = note + "\n\n" + text;
        return new Email(answer.to(), subject, html, plain, Map.of(), answer.key(), answer.contactName(), answer.contactAddress(), List.of());
    }
}
