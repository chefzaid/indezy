package dev.swirlit.indezy.service.account;

import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mail.AccountDeletionEmailComposer;
import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.AccountEventService;
import dev.swirlit.indezy.service.AccountSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountDeletionServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final AccountSessionService sessions = mock(AccountSessionService.class);
    private final AccountEventService events = mock(AccountEventService.class);
    private final AccountPurge purge = mock(AccountPurge.class);
    private final ResendMailer mailer = mock(ResendMailer.class);
    private final AccountDeletionEmailComposer composer = mock(AccountDeletionEmailComposer.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T09:00:00Z"), ZoneId.of("Europe/Paris"));
    private final AccountDeletionService service = new AccountDeletionService(users, sessions, events, purge, mailer, composer, transactions,
        clock);

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("camille@example.test");
        user.setFirstName("Camille");
        user.setLanguagePreference("en");
        return user;
    }

    @Test
    @SuppressWarnings("unchecked")
    void putsTheAccountOnHoldAndEmailsTheDayItGoes() throws Exception {
        User camille = user(1L);
        when(users.findById(1L)).thenReturn(Optional.of(camille));
        when(mailer.configured()).thenReturn(true);
        Email email = new Email("camille@example.test", "s", "h", "t", Map.of(), "k");
        when(composer.compose(eq("Camille"), eq("camille@example.test"), any(), eq(LocalDate.of(2026, 12, 17)), anyString())).thenReturn(email);
        assertThat(service.request(1L, "CAMILLE@example.test")).isEqualTo(LocalDate.of(2026, 12, 17));
        verify(sessions).endAll(1L);
        verify(mailer).send(email);
        assertThat(service.restorable(camille)).isTrue();

        // A mail that fails does not undo the hold.
        User again = user(2L);
        when(users.findById(2L)).thenReturn(Optional.of(again));
        when(mailer.send(any())).thenThrow(new MailException("refused"));
        assertThat(service.request(2L, "camille@example.test")).isNotNull();
        assertThatThrownBy(() -> service.request(3L, "x")).isInstanceOf(ResourceNotFoundException.class);
        User other = user(4L);
        when(users.findById(4L)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.request(4L, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void purgesOnlyAccountsStillOnHoldPastTheirThirtyDays() {
        User due = user(1L);
        due.setDeletedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        User restored = user(2L);
        User disabled = user(3L);
        disabled.setDeletedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        disabled.setDisabledAt(LocalDateTime.of(2026, 10, 2, 9, 0));
        when(users.heldBefore(any())).thenReturn(List.of(1L, 2L, 3L, 4L));
        when(users.findById(1L)).thenReturn(Optional.of(due));
        when(users.findById(2L)).thenReturn(Optional.of(restored));
        when(users.findById(3L)).thenReturn(Optional.of(disabled));
        when(users.findById(4L)).thenThrow(new IllegalStateException("database down"));
        when(purge.purge(1L)).thenReturn(40);
        when(transactions.execute(any())).thenAnswer(call -> ((TransactionCallback<Integer>) call.getArgument(0)).doInTransaction(null));
        assertThat(service.purgeDue()).isOne();
        verify(purge, never()).purge(2L);
        verify(purge, never()).purge(3L);
        assertThat(service.restorable(disabled)).isFalse();
        assertThat(service.restorable(due)).isFalse();

        when(users.heldBefore(any())).thenReturn(List.of());
        assertThat(service.purgeDue()).isZero();
    }
}
