package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.LoginResponse;
import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.mail.AccountEmailComposer;
import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * The email links of an account signed in with a password (see docs/features.md#sign-in): confirming its address after
 * registering, when {@code indezy.auth.email-verification} requires it (production), and setting a new password. Each
 * link holds a 256-bit secret of which only the SHA-256 is kept; asking again sends one email every two minutes at most,
 * and asking for an unknown address answers the same, so nothing tells whether an account exists.
 */
@Slf4j
@Service
@lombok.RequiredArgsConstructor
public class AccountEmailService {

    static final Duration RESET_VALID = Duration.ofHours(1);
    static final Duration RESEND_AFTER = Duration.ofMinutes(2);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final ResendMailer mailer;
    private final AccountEmailComposer composer;
    private final PasswordEncoder passwords;
    private final AccountSessionService sessions;
    private final AccountEventService events;
    private final Clock clock;

    /** In production, a new account confirms its email before signing in; off elsewhere. */
    @Value("${indezy.auth.email-verification:false}")
    private boolean verificationRequired;

    /** Whether a new account must confirm its email before it signs in. */
    public boolean verificationRequired() {
        return verificationRequired;
    }

    /** Whether the account still has to confirm its email. */
    public static boolean unverified(User user) {
        return Boolean.FALSE.equals(user.getEmailVerified());
    }

    /** Marks a new account unverified and sends it the link confirming its address. */
    @Transactional
    public void requireVerification(User user) {
        user.setEmailVerified(false);
        sendVerification(user);
    }

    /** Sends the confirmation link again to an unverified account, at most every two minutes; nothing for any other address. */
    @Transactional
    public void resendVerification(String email) {
        account(email).filter(AccountEmailService::unverified).filter(this::mayEmail).ifPresent(this::sendVerification);
    }

    /** An account confirmed, with the session its link opened. */
    public record Verified(Long userId, String session) {
    }

    /** Confirms the address the link was sent to and opens a session; the link works once. */
    @Transactional
    public Verified verify(String token) {
        User user = users.findByEmailVerificationHash(hash(token))
            .orElseThrow(() -> new FieldRejectedException("token", "This link was already used or is not valid"));
        user.setEmailVerified(true);
        user.setEmailVerificationHash(null);
        events.record(user.getId(), AccountEventType.EMAIL_VERIFIED, null);
        return new Verified(user.getId(), sessions.open(user, dev.swirlit.indezy.model.enums.SignInMethod.EMAIL_LINK));
    }

    /** Sends the link setting a new password to an account signed in with one, at most every two minutes; nothing otherwise. */
    @Transactional
    public void requestReset(String email) {
        account(email).filter(user -> user.getPasswordHash() != null && user.isActive()).filter(this::mayEmail).ifPresent(user -> {
            String token = token();
            user.setPasswordResetHash(hash(token));
            user.setPasswordResetExpiresAt(clock.instant().plus(RESET_VALID));
            send(composer.reset(user.getEmail(), user.getFirstName(), token, language(user)), user);
        });
    }

    /**
     * Sets the new password the link was sent for, within the hour: the link then works no more, every session of the
     * account ends, and a session opens here. A password reset also confirms the address it was sent to.
     */
    @Transactional
    public User reset(String token, String password) {
        User user = users.findByPasswordResetHash(hash(token))
            .filter(found -> found.getPasswordResetExpiresAt() != null && found.getPasswordResetExpiresAt().isAfter(clock.instant()))
            .orElseThrow(() -> new FieldRejectedException("token", "This link has expired or was already used: ask for a new one"));
        user.setPasswordHash(passwords.encode(password));
        user.setPasswordResetHash(null);
        user.setPasswordResetExpiresAt(null);
        if (unverified(user)) {
            user.setEmailVerified(true);
            user.setEmailVerificationHash(null);
        }
        sessions.endAll(user.getId());
        events.record(user.getId(), AccountEventType.PASSWORD_RESET, null);
        return user;
    }

    /** What registering answers while verification is required: no session, the email said to wait in the inbox. */
    public static LoginResponse awaitingVerification() {
        LoginResponse response = new LoginResponse();
        response.setEmailVerificationRequired(true);
        return response;
    }

    private void sendVerification(User user) {
        String token = token();
        user.setEmailVerificationHash(hash(token));
        send(composer.verification(user.getEmail(), user.getFirstName(), token, language(user)), user);
    }

    private void send(Email email, User user) {
        user.setAccountEmailSentAt(clock.instant());
        if (!mailer.configured()) {
            log.warn("No Resend key: the account email of account {} was not sent", user.getId());
            return;
        }
        try {
            mailer.send(email);
        } catch (MailException e) {
            log.warn("The account email of account {} could not be sent: {}", user.getId(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean mayEmail(User user) {
        Instant last = user.getAccountEmailSentAt();
        return last == null || last.plus(RESEND_AFTER).isBefore(clock.instant());
    }

    private Optional<User> account(String email) {
        return email == null || email.isBlank() ? Optional.empty() : users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
            .or(() -> users.findByEmail(email.strip()));
    }

    private static DocumentLanguage language(User user) {
        return "en".equalsIgnoreCase(user.getLanguagePreference()) ? DocumentLanguage.EN : DocumentLanguage.FR;
    }

    private static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing", e);
        }
    }
}
