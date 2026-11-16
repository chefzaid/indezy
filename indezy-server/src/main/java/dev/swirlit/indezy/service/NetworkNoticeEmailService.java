package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkDtos;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.NetworkNoticeEmailComposer;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.NetworkNotification;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.NetworkNotificationRepository;
import dev.swirlit.indezy.repository.NetworkPostRepository;
import dev.swirlit.indezy.repository.NetworkRestrictionRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Emails a member the network notifications of the kinds they chose (see docs/features.md#member-network), still unread an
 * hour after they came: one email for all, grouped as on the notifications page, at most every three hours. Each email
 * is claimed before it is sent (the notifications marked emailed in a transaction the member's version guards), so two
 * replicas never send it twice, and given back when Resend refuses it. Nothing is sent while no Resend key is set or the
 * network is off.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkNoticeEmailService {

    static final Duration UNREAD_FOR = Duration.ofHours(1);
    static final Duration AT_MOST_EVERY = Duration.ofHours(3);
    static final int GROUPS_SHOWN = 10;
    static final int EXCERPT = 60;
    private static final int MAX_TOKEN_LENGTH = 64;

    /** An email claimed for sending, and what it marked, to give back. */
    private record Claim(Email email, Instant previousSentAt, List<Long> marked) {
    }

    private final NetworkService network;
    private final NetworkMemberRepository members;
    private final NetworkNotificationRepository notifications;
    private final NetworkRestrictionRepository restrictions;
    private final NetworkPostRepository posts;
    private final UserRepository users;
    private final NetworkNoticeEmailComposer composer;
    private final FeatureFlagService flags;
    private final ResendMailer mailer;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public int sendDue() throws InterruptedException {
        return send(clock.instant());
    }

    /** Sends the emails due at {@code now}: for the notifications unread an hour before it; how many were sent. */
    public int send(Instant now) throws InterruptedException {
        if (!mailer.configured() || !flags.isEnabled(FeatureFlag.SOCIAL_NETWORK)) {
            return 0;
        }
        // Creation times are written in the server's zone.
        LocalDateTime before = LocalDateTime.ofInstant(now.minus(UNREAD_FOR), ZoneId.systemDefault());
        int sent = 0;
        for (Long userId : notifications.recipientsToEmail(before)) {
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
                    log.warn("Account {}'s notifications email was not sent: {}", userId, e.getMessage());
                }
            } catch (OptimisticLockingFailureException e) {
                log.debug("Account {}'s notifications email was claimed elsewhere", userId);
            }
        }
        return sent;
    }

    /** Stops every notifications email of the account whose one-click unsubscribe holds {@code token}. */
    @Transactional
    public void unsubscribe(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new ResourceNotFoundException("Unknown unsubscribe link");
        }
        User user = users.findByMessageEmailToken(token.strip()).orElseThrow(() -> new ResourceNotFoundException("Unknown unsubscribe link"));
        members.findByUserId(user.getId()).ifPresent(member -> member.setNotificationsEmailed(null));
        log.info("Account {} stopped its notifications emails from its email", user.getId());
    }

    private Claim claim(Long userId, LocalDateTime before, Instant now) {
        User user = users.findById(userId).orElse(null);
        NetworkMember member = members.findByUserId(userId).filter(NetworkMember::isActive).orElse(null);
        if (user == null || member == null || !user.isActive() ||
            member.getNotificationEmailSentAt() != null && member.getNotificationEmailSentAt().plus(AT_MOST_EVERY).isAfter(now)) {
            return null;
        }
        Set<NetworkNotificationType> emailed = NetworkNotificationService.emailed(member);
        if (emailed.isEmpty()) {
            return null;
        }
        Set<Long> blocked = Set.copyOf(restrictions.blockedEitherWay(userId));
        Map<String, List<NetworkNotification>> groups = new LinkedHashMap<>();
        List<Long> marked = new ArrayList<>();
        for (NetworkNotification notification : notifications.toEmail(userId, before)) {
            if (emailed.contains(notification.getType()) && !blocked.contains(notification.getActorId())) {
                groups.computeIfAbsent(notification.getType() + ":" + notification.getPostId(), key -> new ArrayList<>()).add(notification);
                notification.setEmailed(true);
                marked.add(notification.getId());
            }
        }
        List<NetworkNoticeEmailComposer.Line> lines = groups.values().stream().limit(GROUPS_SHOWN).map(this::line).flatMap(Optional::stream)
            .toList();
        if (lines.isEmpty()) {
            return null;
        }
        Instant previousSentAt = member.getNotificationEmailSentAt();
        member.setNotificationEmailSentAt(now);
        if (user.getMessageEmailToken() == null) {
            user.setMessageEmailToken(Tokens.random());
        }
        DocumentLanguage language = "en".equalsIgnoreCase(user.getLanguagePreference()) ? DocumentLanguage.EN : DocumentLanguage.FR;
        Email email = composer.compose(new NetworkNoticeEmailComposer.Notices(user.getFirstName(), user.getEmail(), language,
            user.getMessageEmailToken(), lines, "network-notices/" + userId + "/" + now.getEpochSecond()));
        return new Claim(email, previousSentAt, marked);
    }

    /** A group as one line: its first member still in reach, how many others, and its post's first words. */
    private Optional<NetworkNoticeEmailComposer.Line> line(List<NetworkNotification> group) {
        List<Long> actors = group.stream().map(NetworkNotification::getActorId).distinct().toList();
        Optional<NetworkDtos.MemberCard> first = actors.stream().map(actor -> members.findByUserId(actor).filter(NetworkMember::isActive)
            .flatMap(network::liveCard)).flatMap(Optional::stream).findFirst();
        Long postId = group.getFirst().getPostId();
        String excerpt = postId == null ? null : posts.findById(postId).map(post -> post.getText().replaceAll("\\s+", " ").strip())
            .map(text -> text.length() > EXCERPT ? text.substring(0, EXCERPT - 1) + "…" : text).orElse(null);
        return first.map(card -> new NetworkNoticeEmailComposer.Line(group.getFirst().getType(), (card.firstName() + " " + card.lastName()).strip(),
            actors.size() - 1, excerpt));
    }

    private void giveBack(Long userId, Claim claim) {
        members.findByUserId(userId).ifPresent(member -> member.setNotificationEmailSentAt(claim.previousSentAt()));
        notifications.findAllById(claim.marked()).forEach(notification -> notification.setEmailed(null));
    }
}
