package dev.swirlit.indezy.service;

import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.NetworkMessageEmailComposer;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.NetworkConversationMember;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.repository.NetworkConversationMemberRepository;
import dev.swirlit.indezy.repository.NetworkConversationRepository;
import dev.swirlit.indezy.repository.NetworkMessageRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.util.Tokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Emails a member the messages still unread an hour after they came (see docs/features.md#member-network), one email for
 * all their conversations, at most every three hours, unless they turned these emails off or muted the conversation.
 * Each email is claimed before it is sent (the conversations' {@code emailedUntil} and the account's
 * {@code messageEmailSentAt} move on in a transaction their versions guard), so two replicas never send it twice, and
 * given back when Resend refuses it. Nothing is sent while no Resend key is set or the network is off.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkMessageEmailService {

    static final Duration UNREAD_FOR = Duration.ofHours(1);
    static final Duration AT_MOST_EVERY = Duration.ofHours(3);
    private static final int MAX_TOKEN_LENGTH = 64;

    /** An email claimed for sending, and what it moved on, to give back. */
    private record Claim(Email email, Instant previousSentAt, Map<Long, LocalDateTime> previousUntil) {
    }

    private final NetworkService network;
    private final NetworkConversations shared;
    private final NetworkConversationMemberRepository participants;
    private final NetworkConversationRepository conversations;
    private final NetworkMessageRepository messages;
    private final UserRepository users;
    private final NetworkMessageEmailComposer composer;
    private final FeatureFlagService flags;
    private final ResendMailer mailer;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /** Sends the emails due now; how many were sent. */
    public int sendDue() throws InterruptedException {
        return send(clock.instant());
    }

    /** Sends the emails due at {@code now}: for the messages unread an hour before it; how many were sent. */
    public int send(Instant now) throws InterruptedException {
        if (!mailer.configured() || !flags.isEnabled(FeatureFlag.SOCIAL_NETWORK)) {
            return 0;
        }
        // Creation times are written in the server's zone.
        LocalDateTime before = LocalDateTime.ofInstant(now.minus(UNREAD_FOR), ZoneId.systemDefault());
        int sent = 0;
        for (Long userId : participants.withUnemailedMessages(before)) {
            try {
                Claim claim = transactions.execute(status -> claim(userId, before, now));
                if (claim == null) {
                    continue;
                }
                try {
                    mailer.send(claim.email());
                    sent++;
                } catch (MailException e) {
                    transactions.executeWithoutResult(status -> giveBack(userId, claim));
                    log.warn("Account {}'s unread messages email was not sent: {}", userId, e.getMessage());
                }
            } catch (OptimisticLockingFailureException e) {
                log.debug("Account {}'s unread messages email was claimed elsewhere", userId);
            }
        }
        return sent;
    }

    /** Stops the unread messages emails of the account whose one-click unsubscribe holds {@code token}. */
    @Transactional
    public void unsubscribe(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new ResourceNotFoundException("Unknown unsubscribe link");
        }
        User user = users.findByMessageEmailToken(token.strip()).orElseThrow(() -> new ResourceNotFoundException("Unknown unsubscribe link"));
        user.setMessageEmails(false);
        log.info("Account {} stopped its unread messages emails from its email", user.getId());
    }

    /** The account's email of the conversations waiting, or null when none is due or the account wants none. */
    private Claim claim(Long userId, LocalDateTime before, Instant now) {
        User user = users.findById(userId).orElse(null);
        if (user == null || !user.isActive() || Boolean.FALSE.equals(user.getMessageEmails()) || !network.isMember(userId) ||
            user.getMessageEmailSentAt() != null && user.getMessageEmailSentAt().plus(AT_MOST_EVERY).isAfter(now)) {
            return null;
        }
        List<NetworkMessageEmailComposer.Waiting> waiting = new ArrayList<>();
        Map<Long, LocalDateTime> previousUntil = new HashMap<>();
        for (NetworkConversationMember participant : participants.findByUserId(userId)) {
            if (participant.isMuted()) {
                continue;
            }
            LocalDateTime after = later(participant.getLastReadAt(), participant.getEmailedUntil());
            long count = messages.between(participant.getConversationId(), userId, after, before);
            if (count == 0) {
                continue;
            }
            conversations.findById(participant.getConversationId()).flatMap(conversation -> shared.seen(userId, conversation)).ifPresent(seen -> {
                waiting.add(new NetworkMessageEmailComposer.Waiting(seen.label(), count));
                previousUntil.put(participant.getId(), participant.getEmailedUntil());
                participant.setEmailedUntil(messages.latestBetween(participant.getConversationId(), userId, after, before));
            });
        }
        if (waiting.isEmpty()) {
            return null;
        }
        Instant previousSentAt = user.getMessageEmailSentAt();
        user.setMessageEmailSentAt(now);
        if (user.getMessageEmailToken() == null) {
            user.setMessageEmailToken(Tokens.random());
        }
        DocumentLanguage language = "en".equalsIgnoreCase(user.getLanguagePreference()) ? DocumentLanguage.EN : DocumentLanguage.FR;
        Email email = composer.compose(new NetworkMessageEmailComposer.Unread(userId, user.getFirstName(), user.getEmail(), language,
            user.getMessageEmailToken(), waiting, "network-messages/" + userId + "/" + now.getEpochSecond()));
        return new Claim(email, previousSentAt, previousUntil);
    }

    private void giveBack(Long userId, Claim claim) {
        users.findById(userId).ifPresent(user -> user.setMessageEmailSentAt(claim.previousSentAt()));
        participants.findAllById(claim.previousUntil().keySet())
            .forEach(participant -> participant.setEmailedUntil(claim.previousUntil().get(participant.getId())));
    }

    private static LocalDateTime later(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        return second == null || first.isAfter(second) ? first : second;
    }
}
