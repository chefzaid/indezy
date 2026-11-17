package dev.swirlit.indezy.service.account;

import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mail.AccountDeletionEmailComposer;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.AccountEventService;
import dev.swirlit.indezy.service.AccountSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Account deletion (see docs/security.md#data-privacy): asked for by the account, typing its email to confirm, it puts
 * the account on hold at once (no sign-in, every session ended, hidden from the network and the public profile) and
 * emails the day it goes; signing in within 30 days offers to restore it; after 30 days, everything it holds is deleted
 * for good, every night.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

    public static final Duration HOLD = Duration.ofDays(30);

    private final UserRepository users;
    private final AccountSessionService sessions;
    private final AccountEventService events;
    private final AccountPurge purge;
    private final ResendMailer mailer;
    private final AccountDeletionEmailComposer composer;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /** When an account on hold is deleted for good. */
    public static LocalDate purgeOn(User user) {
        return user.getDeletedAt().plus(HOLD).toLocalDate();
    }

    /** Whether the account is on hold and may still be restored: asked for less than 30 days ago, not disabled. */
    public boolean restorable(User user) {
        return user.getDeletedAt() != null && user.getDisabledAt() == null &&
            user.getDeletedAt().plus(HOLD).isAfter(LocalDateTime.now(clock));
    }

    /** Puts the account on hold, once its email is typed to confirm; answers the day it will be deleted. */
    @Transactional
    public LocalDate request(Long userId, String confirmation) {
        User user = users.findById(userId).filter(User::isActive).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (confirmation == null || !confirmation.strip().equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("Type the account's email address to confirm");
        }
        user.setDeletedAt(LocalDateTime.now(clock));
        users.save(user);
        int ended = sessions.endAll(userId);
        LocalDate purgeOn = purgeOn(user);
        events.record(userId, AccountEventType.ACCOUNT_DELETION_REQUESTED, purgeOn.toString());
        log.info("Account {} asked for its deletion: on hold until {}, {} sessions ended", userId, purgeOn, ended);
        notify(user, purgeOn);
        return purgeOn;
    }

    /** Takes the account off hold, as it was. */
    @Transactional
    public void restore(User user) {
        user.setDeletedAt(null);
        users.save(user);
        events.record(user.getId(), AccountEventType.ACCOUNT_RESTORED);
        log.info("Account {} restored from its hold", user.getId());
    }

    /** Deletes for good, every night, the accounts on hold for 30 days, each in its own transaction; answers how many. */
    @Scheduled(cron = "0 20 4 * * *", zone = "Europe/Paris")
    public int purgeDue() {
        List<Long> due = users.heldBefore(LocalDateTime.now(clock).minus(HOLD));
        int purged = 0;
        for (Long userId : due) {
            try {
                Integer rows = transactions.execute(status -> users.findById(userId)
                    .filter(user -> user.getDeletedAt() != null && user.getDisabledAt() == null && !restorable(user))
                    .map(user -> purge.purge(userId)).orElse(0));
                purged += rows != null && rows > 0 ? 1 : 0;
            } catch (RuntimeException e) {
                log.error("Account {} could not be purged: {}", userId, e.getClass().getSimpleName(), e);
            }
        }
        if (!due.isEmpty()) {
            log.info("Purged {} of {} accounts due", purged, due.size());
        }
        return purged;
    }

    private void notify(User user, LocalDate purgeOn) {
        if (!mailer.configured()) {
            return;
        }
        DocumentLanguage language = "en".equalsIgnoreCase(user.getLanguagePreference()) ? DocumentLanguage.EN : DocumentLanguage.FR;
        try {
            mailer.send(composer.compose(user.getFirstName(), user.getEmail(), language, purgeOn,
                "account-deletion-" + user.getId() + "-" + purgeOn.toString().toLowerCase(Locale.ROOT)));
        } catch (MailException e) {
            log.warn("The deletion email of account {} could not be sent: {}", user.getId(), e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
