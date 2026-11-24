package dev.swirlit.indezy.service;

import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.MailProperties;
import dev.swirlit.indezy.mail.ReplyForwardComposer;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.mail.WebhookSignature;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.InteractionChannel;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Answers to emails sent from Indezy (see docs/features.md#emails-sent-from-indezy): Resend's {@code email.received}
 * webhook, its signature checked, for a reply address {@code reply+<token>@<reply domain>}: the email it answers is
 * marked answered, the answer recorded as an incoming exchange of the contact (which stops a reminder and counts in the
 * campaigns' results), and forwarded to the freelancer, a webhook delivered twice counting once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundEmailService {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern ADDRESS = Pattern.compile("reply\\+([0-9a-f]{40})@(.+)");
    private static final Pattern INBOX = Pattern.compile("inbox\\+([0-9a-f]{40})@(.+)");

    private final MailProperties properties;
    private final ResendMailer mailer;
    private final ReplyForwardComposer composer;
    private final ContactInteractionRepository interactions;
    private final UserRepository users;
    private final Clock clock;

    private AnswerFollowThroughService followThrough;
    private InboxWatcherService watcher;

    /** What sorts the answers with the AI and acts on them; without it, they stay unsorted. */
    @Autowired(required = false)
    void setFollowThrough(AnswerFollowThroughService answerFollowThrough) {
        this.followThrough = answerFollowThrough;
    }

    /** The inbox watcher, for the emails forwarded to a member's {@code inbox+<token>} address. */
    @Autowired(required = false)
    void setWatcher(InboxWatcherService inboxWatcher) {
        this.watcher = inboxWatcher;
    }

    /** What came of a webhook: refused (bad signature), ignored (not an answer to an email of Indezy), or recorded. */
    public enum Outcome {
        REFUSED,
        IGNORED,
        RECORDED
    }

    /** The webhook's headers and raw body, as Resend signs them. */
    public record Delivery(String id, String timestamp, String signature, String body) {

        @Override
        public String toString() {
            return "Delivery[" + id + "]";
        }
    }

    @Transactional
    public Outcome receive(Delivery delivery) throws InterruptedException {
        if (!properties.replies() || !WebhookSignature.valid(properties.inboundSecret(), delivery.id(), delivery.timestamp(),
                delivery.signature(), delivery.body(), clock.instant())) {
            return Outcome.REFUSED;
        }
        JsonNode event = JSON.readTree(delivery.body());
        JsonNode data = event.path("data");
        String emailId = data.path("email_id").asString("");
        if (!"email.received".equals(event.path("type").asString("")) || emailId.isBlank() || interactions.existsByInboundEmailId(emailId)) {
            return Outcome.IGNORED;
        }
        List<String> recipients = recipients(data);
        Optional<ContactInteraction> answered = answered(recipients);
        if (answered.isEmpty()) {
            return forwarded(recipients, emailId, data.path("from").asString(""));
        }
        ContactInteraction original = answered.get();
        Contact contact = original.getContact();
        ResendMailer.Received received = mailer.received(emailId);
        original.setReplied(true);
        ContactInteraction answer = new ContactInteraction();
        answer.setContact(contact);
        answer.setProject(original.getProject());
        answer.setOccurredOn(LocalDate.now(clock.withZone(PARIS)));
        answer.setChannel(InteractionChannel.EMAIL);
        answer.setDirection(InteractionDirection.INCOMING);
        String subject = received.subject().isBlank() ? original.getSummary() : received.subject().strip();
        answer.setSummary(subject.length() > ContactInteraction.SUMMARY_MAX ? subject.substring(0, ContactInteraction.SUMMARY_MAX) : subject);
        answer.setInboundEmailId(emailId);
        AnswerFollowThroughService sorter = followThrough;
        if (sorter != null) {
            sorter.followThrough(answer, original, subject, received.text(), answer.getOccurredOn());
        }
        interactions.save(answer);
        String freelancer = contact.getFreelance() == null ? null : contact.getFreelance().getEmail();
        if (freelancer != null && !freelancer.isBlank()) {
            DocumentLanguage language = users.findByEmail(freelancer)
                .map(user -> "en".equalsIgnoreCase(user.getLanguagePreference()) ? DocumentLanguage.EN : DocumentLanguage.FR).orElse(DocumentLanguage.FR);
            String from = data.path("from").asString(contact.getEmail());
            try {
                mailer.send(composer.compose(new ReplyForwardComposer.Answer(freelancer.strip(), contact.getFullName(), from, subject,
                    received.text(), language, "reply-forward-" + emailId)));
            } catch (MailException e) {
                // The answer stays recorded: the member reads it in Resend's received emails or the contact's exchanges.
                log.warn("The answer {} could not be forwarded: {}", emailId, e.getMessage());
            }
        }
        return Outcome.RECORDED;
    }

    /** "reply+…@replies.swirlit.dev" from "Name <reply+…@Replies.swirlit.dev>", in lower case. */
    static String address(String recipient) {
        int open = recipient.indexOf('<');
        int close = recipient.indexOf('>', open + 1);
        String address = open >= 0 && close > open ? recipient.substring(open + 1, close) : recipient;
        return address.strip().toLowerCase(Locale.ROOT);
    }

    private static List<String> recipients(JsonNode data) {
        List<String> recipients = new ArrayList<>();
        data.path("to").forEach(node -> recipients.add(node.asString("")));
        data.path("received_for").forEach(node -> recipients.add(node.asString("")));
        return recipients;
    }

    /** An email a member forwarded to their inbox watcher address, handed to the watcher once; ignored otherwise. */
    private Outcome forwarded(List<String> recipients, String emailId, String from) throws InterruptedException {
        InboxWatcherService inbox = watcher;
        String domain = properties.replyDomain().strip().toLowerCase(Locale.ROOT);
        for (String recipient : recipients) {
            Matcher match = INBOX.matcher(address(recipient));
            if (inbox != null && match.matches() && match.group(2).equals(domain)) {
                Optional<User> owner = users.findByInboxToken(match.group(1));
                if (owner.isPresent()) {
                    if (inbox.seen(emailId)) {
                        return Outcome.IGNORED;
                    }
                    inbox.receive(owner.get(), emailId, from, mailer.received(emailId));
                    return Outcome.RECORDED;
                }
            }
        }
        return Outcome.IGNORED;
    }

    /** The email of Indezy an answer is for: the first of its recipients that is a reply address of the reply domain. */
    private Optional<ContactInteraction> answered(List<String> recipients) {
        String domain = properties.replyDomain().strip().toLowerCase(Locale.ROOT);
        for (String recipient : recipients) {
            Matcher match = ADDRESS.matcher(address(recipient));
            if (match.matches() && match.group(2).equals(domain)) {
                Optional<ContactInteraction> found = interactions.findByReplyToken(match.group(1));
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
