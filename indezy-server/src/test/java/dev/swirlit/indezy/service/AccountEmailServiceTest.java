package dev.swirlit.indezy.service;

import dev.swirlit.indezy.exception.FieldRejectedException;
import dev.swirlit.indezy.mail.AccountEmailComposer;
import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountEmailServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final ResendMailer mailer = mock(ResendMailer.class);
    private final AccountEmailComposer composer = mock(AccountEmailComposer.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AccountSessionService sessions = mock(AccountSessionService.class);
    private final AccountEventService events = mock(AccountEventService.class);
    private final AccountEmailService service = new AccountEmailService(users, mailer, composer, passwords, sessions, events,
        Clock.fixed(NOW, ZoneOffset.UTC));
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(5L);
        user.setEmail("alice@example.com");
        user.setFirstName("Alice");
        user.setPasswordHash("hash");
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(composer.reset(any(), any(), any(), any())).thenReturn(mock(Email.class));
        when(composer.verification(any(), any(), any(), any())).thenReturn(mock(Email.class));
        when(passwords.encode(anyString())).thenReturn("new-hash");
    }

    @Test
    void aResetWithinTheHourSetsThePasswordOnceAndConfirmsTheAddressItWasSentTo() throws Exception {
        when(mailer.configured()).thenReturn(true);
        user.setEmailVerified(false);
        user.setEmailVerificationHash("pending");
        service.requestReset("  Alice@Example.com ");
        verify(mailer).send(any());
        String token = "the-token";
        user.setPasswordResetHash(AccountEmailService.hash(token));
        when(users.findByPasswordResetHash(AccountEmailService.hash(token))).thenReturn(Optional.of(user));

        assertThat(service.reset(token, "N3w-password!")).isSameAs(user);
        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getPasswordResetHash()).isNull();
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getEmailVerificationHash()).isNull();
        verify(sessions).endAll(5L);
        verify(events).record(5L, AccountEventType.PASSWORD_RESET, null);
    }

    @Test
    void refusesAnExpiredOrUnknownResetLink() {
        user.setPasswordResetExpiresAt(NOW.minusSeconds(1));
        when(users.findByPasswordResetHash(AccountEmailService.hash("old"))).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.reset("old", "N3w-password!")).isInstanceOf(FieldRejectedException.class);
        assertThatThrownBy(() -> service.reset(null, "N3w-password!")).isInstanceOf(FieldRejectedException.class);
        assertThat(user.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void sendsAnAccountEmailEveryTwoMinutesAtMostAndNothingForAnUnknownOrSsoAccount() throws Exception {
        when(mailer.configured()).thenReturn(true);
        service.requestReset("alice@example.com");
        // Asked again a minute later: no second email.
        user.setAccountEmailSentAt(NOW.minusSeconds(60));
        service.requestReset("alice@example.com");
        verify(mailer).send(any());

        service.requestReset("nobody@example.com");
        service.requestReset(" ");
        user.setAccountEmailSentAt(null);
        user.setPasswordHash(null);
        service.requestReset("alice@example.com");
        verify(mailer).send(any());
    }

    @Test
    void keepsTheLinkWhenTheEmailCannotLeave() throws Exception {
        user.setEmailVerified(false);
        when(mailer.configured()).thenReturn(false);
        service.resendVerification("alice@example.com");
        verify(mailer, never()).send(any());
        assertThat(user.getEmailVerificationHash()).isNotNull();
        assertThat(user.getAccountEmailSentAt()).isEqualTo(NOW);

        when(mailer.configured()).thenReturn(true);
        when(mailer.send(any())).thenThrow(new MailException("Resend answered 500"));
        user.setAccountEmailSentAt(null);
        service.requestReset("alice@example.com");
        assertThat(user.getPasswordResetHash()).isNotNull();
    }

    @Test
    void confirmsAnAddressOnceThenRefusesTheSameLink() {
        user.setEmailVerified(false);
        when(users.findByEmailVerificationHash(AccountEmailService.hash("tok"))).thenReturn(Optional.of(user));
        when(sessions.open(any(), any())).thenReturn("session");
        assertThat(service.verify("tok")).isEqualTo(new AccountEmailService.Verified(5L, "session"));
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getEmailVerificationHash()).isNull();
        when(users.findByEmailVerificationHash(AccountEmailService.hash("tok"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verify("tok")).isInstanceOf(FieldRejectedException.class);
    }
}
