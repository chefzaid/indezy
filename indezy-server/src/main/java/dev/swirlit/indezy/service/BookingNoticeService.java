package dev.swirlit.indezy.service;

import dev.swirlit.indezy.mail.BookingEmailComposer;
import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.Booking;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.BookingRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Emails the freelancer each call a recruiter books through the booking link (see docs/features.md#booking-link),
 * unless the account turned these emails off. Each email is claimed before it is sent (the call's
 * {@code noticeSentAt} set only where still empty), so two replicas never send it twice, and given back when Resend
 * refuses it, to try again a minute later for a day. Nothing is sent while no Resend key is set.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingNoticeService {

    static final Duration TRIED_FOR = Duration.ofDays(1);

    private final BookingRepository bookings;
    private final FreelanceRepository freelances;
    private final UserRepository users;
    private final BookingEmailComposer composer;
    private final ResendMailer mailer;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /** Sends the emails of the calls booked lately and not told yet; how many were sent. */
    public int sendDue() throws InterruptedException {
        if (!mailer.configured()) {
            return 0;
        }
        Instant now = clock.instant();
        int sent = 0;
        // Creation times are written in the server's zone.
        for (Long id : bookings.findIdsAwaitingNotice(LocalDateTime.ofInstant(now.minus(TRIED_FOR), ZoneId.systemDefault()))) {
            Email email = transactions.execute(status -> claim(id, now));
            if (email == null) {
                continue;
            }
            try {
                mailer.send(email);
                sent++;
            } catch (MailException e) {
                transactions.executeWithoutResult(status -> bookings.releaseNotice(id));
                log.warn("The email of booking {} was not sent: {}", id, e.getMessage());
            }
        }
        return sent;
    }

    /** Claims the call's email: the email to send, or null when another replica claimed it or the account wants none. */
    private Email claim(Long id, Instant now) {
        if (bookings.claimNotice(id, now) == 0) {
            return null;
        }
        Booking booking = bookings.findById(id).orElse(null);
        User user = booking == null ? null : freelances.findById(booking.getFreelanceId())
            .flatMap(freelance -> users.findByEmail(freelance.getEmail())).orElse(null);
        if (user == null || !user.isActive() || Boolean.FALSE.equals(user.getBookingEmails())) {
            return null;
        }
        return composer.compose(booking, user.getEmail(),
            "en".equalsIgnoreCase(user.getLanguagePreference()) ? DocumentLanguage.EN : DocumentLanguage.FR);
    }
}
