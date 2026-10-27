package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.OfferPageDto;
import dev.swirlit.indezy.dto.OfferSearchRequest;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.mail.WeeklyDigestComposer;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DigestSection;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * The weekly digest (see docs/features.md#weekly-digest): every Monday from 8:00, Paris time, each account that turned
 * it on gets where its pipeline stands, the week's interviews, follow-ups due, opportunities gone quiet, missions
 * ending and the new offers matching its profile. Each digest is claimed before it is sent (the account's
 * {@code weeklyDigestSentOn} moves to the week in a transaction its version guards), so two replicas never send it
 * twice, and given back when Resend refuses it. A week with nothing to say sends nothing. Nothing is sent while no
 * Resend key is set.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyDigestService {

    static final int SEND_HOUR = 8;
    static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final Duration OFFERS_SINCE = Duration.ofDays(7);
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 64;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final OfferPageDto NO_OFFERS = new OfferPageDto(List.of(), 0, 0, 0, 0, false, false);

    /** A digest claimed for sending, and the week the account was sent before. */
    private record Claim(Email email, LocalDate previous) {
    }

    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final DashboardStatsService dashboard;
    private final OfferSearchService offers;
    private final WeeklyDigestComposer composer;
    private final ResendMailer mailer;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /** The Monday of the week whose digest is due at {@code now}, or null before 8:00 on a Monday. */
    static LocalDate dueWeek(Instant now) {
        ZonedDateTime local = now.atZone(PARIS);
        LocalDate monday = local.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return local.isBefore(monday.atTime(SEND_HOUR, 0).atZone(PARIS)) ? null : monday;
    }

    /** Sends the digests that are due; how many were sent. */
    public int sendDue() throws InterruptedException {
        LocalDate week = dueWeek(clock.instant());
        return week == null ? 0 : send(week);
    }

    /** Sends the digest of the week starting on Monday {@code week} to the accounts not sent it yet; how many were sent. */
    public int send(LocalDate week) throws InterruptedException {
        if (!mailer.configured()) {
            return 0;
        }
        int sent = 0;
        for (Long id : users.findIdsDueForWeeklyDigest(week)) {
            try {
                if (sendIfDue(id, week)) {
                    sent++;
                }
            } catch (OptimisticLockingFailureException e) {
                log.debug("Account {}'s weekly digest was claimed elsewhere", id);
            } catch (RuntimeException e) {
                log.warn("Account {}'s weekly digest was not sent: {}", id, e.getMessage());
            }
        }
        return sent;
    }

    /** Stops the digest of the account whose one-click unsubscribe holds {@code token}. */
    @Transactional
    public void unsubscribe(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new ResourceNotFoundException("Unknown weekly digest link");
        }
        User user = users.findByWeeklyDigestToken(token.strip())
            .orElseThrow(() -> new ResourceNotFoundException("Unknown weekly digest link"));
        user.setWeeklyReports(false);
        log.info("Account {} stopped its weekly digest from its email", user.getId());
    }

    /** Gives the account an unsubscribe token when the digest is turned on and it has none yet. */
    public static void prepare(User user) {
        if (Boolean.TRUE.equals(user.getWeeklyReports()) && user.getWeeklyDigestToken() == null) {
            byte[] bytes = new byte[TOKEN_BYTES];
            RANDOM.nextBytes(bytes);
            user.setWeeklyDigestToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        }
    }

    private boolean sendIfDue(Long id, LocalDate week) throws InterruptedException {
        Claim claim = transactions.execute(status -> claim(id, week));
        if (claim == null) {
            return false;
        }
        try {
            mailer.send(claim.email());
        } catch (MailException e) {
            transactions.executeWithoutResult(status -> users.findById(id)
                .filter(user -> week.equals(user.getWeeklyDigestSentOn()))
                .ifPresent(user -> user.setWeeklyDigestSentOn(claim.previous())));
            throw e;
        }
        return true;
    }

    /** Claims the account's digest of {@code week}: the email to send, or null when it is not due or has nothing to say. */
    private Claim claim(Long id, LocalDate week) {
        User user = users.findById(id).orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getWeeklyReports()) || !user.isActive() ||
            (user.getWeeklyDigestSentOn() != null && !user.getWeeklyDigestSentOn().isBefore(week))) {
            return null;
        }
        LocalDate previous = user.getWeeklyDigestSentOn();
        user.setWeeklyDigestSentOn(week);
        prepare(user);
        Freelance freelance = freelances.findByEmail(user.getEmail()).orElse(null);
        if (freelance == null) {
            return null;
        }
        Instant now = clock.instant();
        Set<DigestSection> kept = user.getWeeklyDigestSections();
        OfferPageDto found = kept.contains(DigestSection.OFFERS) ? matchingOffers(freelance.getId(), now) : NO_OFFERS;
        WeeklyDigestComposer.Digest digest = new WeeklyDigestComposer.Digest(user.getId(), user.getFirstName(), user.getEmail(),
            "en".equalsIgnoreCase(user.getLanguagePreference()) ? DocumentLanguage.EN : DocumentLanguage.FR, user.getWeeklyDigestToken(),
            week, WeeklyDigestComposer.Week.of(dashboard.getDashboardStats(freelance.getId(), null)).only(kept), found.content(),
            found.totalElements());
        return digest.isEmpty() ? null : new Claim(composer.compose(digest), previous);
    }

    /** The offers found in the last 7 days that match the profile, best match first; none without profile skills. */
    private OfferPageDto matchingOffers(Long freelanceId, Instant now) {
        if (offers.profile(freelanceId).skills().isEmpty()) {
            return NO_OFFERS;
        }
        OfferSearchRequest request = new OfferSearchRequest();
        request.setMatchesProfile(true);
        request.setSort(OfferSearchRequest.Sort.MATCH);
        return offers.foundBetween(freelanceId, request, now.minus(OFFERS_SINCE), now, WeeklyDigestComposer.ITEMS);
    }
}
