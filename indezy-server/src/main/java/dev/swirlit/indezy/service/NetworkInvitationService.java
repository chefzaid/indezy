package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.NetworkInvitationDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.exception.TooManyRequestsException;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.NetworkInvitationEmailComposer;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.NetworkInvitation;
import dev.swirlit.indezy.model.NetworkInvitationOptOut;
import dev.swirlit.indezy.model.NetworkMember;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.NetworkNotificationType;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.NetworkInvitationOptOutRepository;
import dev.swirlit.indezy.repository.NetworkInvitationRepository;
import dev.swirlit.indezy.repository.NetworkMemberRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.util.Tokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Invitations to the member network (see docs/features.md#member-network): a member emails one of their contacts, once,
 * never one who declined every invitation; the address is kept only as its hash, matched when the person joins, and the
 * member who invited them is then told. The contact is suggested to them as **In your contacts** once a member.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkInvitationService {

    static final String NOT_FOUND = "No such invitation";
    /** How many contacts a member invites a day at most. */
    static final int DAILY_MAX = 20;

    private final NetworkService network;
    private final NetworkInvitationRepository invitations;
    private final NetworkInvitationOptOutRepository optOuts;
    private final NetworkMemberRepository members;
    private final ContactRepository contacts;
    private final FreelanceRepository freelances;
    private final UserRepository users;
    private final NetworkNotificationService notices;
    private final NetworkInvitationEmailComposer composer;
    private final ResendMailer mailer;
    private final Clock clock;

    /** Where one of the reader's contacts stands toward the network: a member, invited, declined, or invitable. */
    @Transactional(readOnly = true)
    public NetworkInvitationDtos.ContactStanding standing(Long userId, Long contactId) {
        network.requireMember(userId);
        return standing(userId, contact(userId, contactId));
    }

    /** Emails the contact an invitation to the network, once, if they are invitable; {@value #DAILY_MAX} a day at most. */
    @Transactional
    public NetworkInvitationDtos.ContactStanding invite(Long userId, Long contactId) {
        network.requireMember(userId);
        Contact contact = contact(userId, contactId);
        if (standing(userId, contact).standing() != NetworkInvitationDtos.Standing.INVITABLE) {
            throw new InvalidStateException("This contact cannot be invited");
        }
        if (invitations.countByInviterIdAndCreatedAtAfter(userId, LocalDateTime.now(clock).minusDays(1)) >= DAILY_MAX) {
            throw new TooManyRequestsException("A member invites " + DAILY_MAX + " contacts a day at most", Duration.ofDays(1));
        }
        if (!mailer.configured()) {
            throw new InvalidStateException("Email is not set up: no invitation can go");
        }
        NetworkInvitation invitation = new NetworkInvitation();
        invitation.setInviterId(userId);
        invitation.setContactId(contactId);
        invitation.setEmailHash(hash(contact.getEmail()));
        invitation.setToken(Tokens.random());
        invitations.save(invitation);
        User inviter = users.findById(userId).orElseThrow();
        DocumentLanguage language = "en".equalsIgnoreCase(inviter.getLanguagePreference()) ? DocumentLanguage.EN : DocumentLanguage.FR;
        try {
            mailer.send(composer.compose(contact.getEmail().strip(), contact.getFirstName(), name(inviter), language, invitation.getToken(),
                "network-invitation-" + invitation.getId()));
        } catch (MailException e) {
            throw new InvalidStateException("The invitation could not be emailed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidStateException("The invitation could not be emailed");
        }
        log.info("Member {} invited contact {} to the network", userId, contactId);
        return standing(userId, contact);
    }

    /** An invitation as the person invited reads it from its link. */
    @Transactional(readOnly = true)
    public NetworkInvitationDtos.PublicInvitation read(String token) {
        NetworkInvitation invitation = invitations.findByToken(token).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        String inviter = users.findById(invitation.getInviterId()).map(NetworkInvitationService::name).orElse("");
        return new NetworkInvitationDtos.PublicInvitation(inviter, optOuts.existsByEmailHash(invitation.getEmailHash()));
    }

    /** The person invited declines every invitation to the network: no member invites the address again. */
    @Transactional
    public NetworkInvitationDtos.PublicInvitation decline(String token) {
        NetworkInvitation invitation = invitations.findByToken(token).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        if (!optOuts.existsByEmailHash(invitation.getEmailHash())) {
            NetworkInvitationOptOut optOut = new NetworkInvitationOptOut();
            optOut.setEmailHash(invitation.getEmailHash());
            optOuts.save(optOut);
            log.info("An address declined the network's invitations");
        }
        return read(token);
    }

    /** A new member was invited: each member who invited them is told. */
    @EventListener
    public void joined(NetworkMemberJoined joined) {
        users.findById(joined.userId()).map(User::getEmail).map(NetworkInvitationService::hash).ifPresent(hash ->
            invitations.findByEmailHashAndJoinedAtIsNull(hash).forEach(invitation -> {
                invitation.setJoinedAt(LocalDateTime.now(clock));
                notices.notify(invitation.getInviterId(), joined.userId(), NetworkNotificationType.INVITE_JOINED, null);
            }));
    }

    private NetworkInvitationDtos.ContactStanding standing(Long userId, Contact contact) {
        String email = contact.getEmail() == null ? "" : contact.getEmail().strip();
        if (email.isEmpty()) {
            return new NetworkInvitationDtos.ContactStanding(NetworkInvitationDtos.Standing.NO_EMAIL, null, null);
        }
        Optional<String> handle = users.findByEmail(email.toLowerCase(Locale.ROOT)).or(() -> users.findByEmail(email))
            .flatMap(user -> members.findByUserId(user.getId())).filter(NetworkMember::isActive).map(NetworkMember::getHandle);
        if (handle.isPresent()) {
            return new NetworkInvitationDtos.ContactStanding(NetworkInvitationDtos.Standing.MEMBER, null, handle.get());
        }
        String hash = hash(email);
        if (optOuts.existsByEmailHash(hash)) {
            return new NetworkInvitationDtos.ContactStanding(NetworkInvitationDtos.Standing.DECLINED, null, null);
        }
        return invitations.findFirstByInviterIdAndEmailHash(userId, hash)
            .map(invitation -> new NetworkInvitationDtos.ContactStanding(NetworkInvitationDtos.Standing.INVITED, invitation.getCreatedAt(), null))
            .orElseGet(() -> new NetworkInvitationDtos.ContactStanding(NetworkInvitationDtos.Standing.INVITABLE, null, null));
    }

    /** One of the reader's workspace contacts; not found otherwise. */
    private Contact contact(Long userId, Long contactId) {
        Long freelanceId = users.findById(userId).flatMap(user -> freelances.findByEmail(user.getEmail())).map(freelance -> freelance.getId())
            .orElseThrow(() -> new ResourceNotFoundException("No such contact"));
        return contacts.findById(contactId).filter(contact -> contact.getFreelance() != null && freelanceId.equals(contact.getFreelance().getId()))
            .orElseThrow(() -> new ResourceNotFoundException("No such contact"));
    }

    private static String name(User user) {
        return Stream.of(user.getFirstName(), user.getLastName()).filter(Objects::nonNull).map(String::strip).filter(part -> !part.isEmpty())
            .collect(Collectors.joining(" "));
    }

    /** The SHA-256 of an address, lower-case, in hexadecimal. */
    static String hash(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(email.strip().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
