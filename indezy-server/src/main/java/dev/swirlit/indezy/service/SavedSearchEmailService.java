package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.OfferPageDto;
import dev.swirlit.indezy.dto.PublicSavedSearchEmailsDto;
import dev.swirlit.indezy.dto.SavedOfferSearchDto;
import dev.swirlit.indezy.dto.SavedSearchEmailsRequest;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mail.Email;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.mail.SavedSearchEmailComposer;
import dev.swirlit.indezy.model.SavedOfferSearch;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.SavedSearchEmails;
import dev.swirlit.indezy.repository.SavedOfferSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * The saved searches' emails (see docs/features.md#saved-search-emails): a search emails the offers Indezy found for it
 * at once, grouped every 30 minutes, or as a daily digest at its hour, Paris time. Each email is claimed before it is
 * sent (the search's {@code emailedUntil} moves on in a transaction its version guards), so two replicas never send
 * it twice, and given back when Resend refuses it, so the next run sends it. Sending stops, and says so, once 60 days
 * of emails went by without an offer opened from them. Nothing is sent while no Resend key is set.
 */
@Service
@Slf4j
public class SavedSearchEmailService {

    static final Duration GROUPED_EVERY = Duration.ofMinutes(30);
    static final Duration STOP_AFTER = Duration.ofDays(60);
    static final int OFFERS_PER_EMAIL = 20;
    static final int DEFAULT_HOUR = 8;
    static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 64;
    private static final List<SavedSearchEmails> SENDING = List.of(SavedSearchEmails.INSTANT, SavedSearchEmails.DAILY);

    /** An email claimed for sending, and what the search held before the claim. */
    private record Claim(Email email, Instant previousUntil, Instant cutoff) {
    }

    private final SavedOfferSearchRepository searches;
    private final SavedOfferSearchService savedSearches;
    private final OfferSearchService offerSearch;
    private final SavedSearchEmailComposer composer;
    private final ResendMailer mailer;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public SavedSearchEmailService(SavedOfferSearchRepository searches, SavedOfferSearchService savedSearches, OfferSearchService offerSearch,
                                   SavedSearchEmailComposer composer, ResendMailer mailer, TransactionTemplate transactions) {
        this(searches, savedSearches, offerSearch, composer, mailer, transactions, Clock.systemUTC());
    }

    SavedSearchEmailService(SavedOfferSearchRepository searches, SavedOfferSearchService savedSearches, OfferSearchService offerSearch,
                            SavedSearchEmailComposer composer, ResendMailer mailer, TransactionTemplate transactions, Clock clock) {
        this.searches = searches;
        this.savedSearches = savedSearches;
        this.offerSearch = offerSearch;
        this.composer = composer;
        this.mailer = mailer;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Sets how often a search emails; turning emails on (again) sends only what Indezy finds from now on and gives the
     * search 60 more days of emails before an unopened run stops them.
     */
    @Transactional
    public SavedOfferSearchDto update(Long freelanceId, Long id, SavedSearchEmailsRequest request) {
        SavedOfferSearch search = searches.findByIdAndFreelanceId(id, freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.SAVED_SEARCH_NOT_FOUND, id)));
        Instant now = clock.instant();
        boolean wasSending = search.getEmailFrequency() != SavedSearchEmails.OFF;
        search.setEmailFrequency(request.frequency());
        if (request.frequency() != SavedSearchEmails.OFF) {
            search.setEmailHour(Objects.requireNonNullElse(request.hour(), DEFAULT_HOUR));
            search.setEmailLanguage(Objects.requireNonNullElse(request.language(), DocumentLanguage.FR));
            if (!wasSending) {
                search.setEmailedUntil(now);
                search.setUnopenedSince(null);
                search.setEmailsStoppedAt(null);
            }
            if (search.getEmailToken() == null) {
                search.setEmailToken(newToken());
            }
        }
        return savedSearches.toDto(search, 0);
    }

    /** An offer was opened from one of the search's emails: they are read, so the 60 days start again. */
    @Transactional
    public void opened(Long freelanceId, Long id) {
        searches.findByIdAndFreelanceId(id, freelanceId).ifPresent(search -> search.setUnopenedSince(null));
    }

    /** What an unsubscribe link shows. */
    @Transactional(readOnly = true)
    public PublicSavedSearchEmailsDto describe(String token) {
        return toPublicDto(byToken(token));
    }

    /** Stops the emails of the search whose unsubscribe link holds {@code token}. */
    @Transactional
    public PublicSavedSearchEmailsDto unsubscribe(String token) {
        SavedOfferSearch search = byToken(token);
        search.setEmailFrequency(SavedSearchEmails.OFF);
        return toPublicDto(search);
    }

    /** Sends the emails that are due; how many were sent. */
    public int sendDue() throws InterruptedException {
        if (!mailer.configured()) {
            return 0;
        }
        // As the database keeps it, so a claim is recognised when it is given back.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int sent = 0;
        for (Long id : searches.findIdsByEmailFrequencyIn(SENDING)) {
            try {
                if (sendIfDue(id, now)) {
                    sent++;
                }
            } catch (OptimisticLockingFailureException e) {
                // Another replica claimed this email first and sends it.
                log.debug("Saved search {} was claimed elsewhere", id);
            } catch (RuntimeException e) {
                log.warn("Saved search {}'s email was not sent: {}", id, e.getMessage());
            }
        }
        return sent;
    }

    private boolean sendIfDue(Long id, Instant now) throws InterruptedException {
        Claim claim = transactions.execute(status -> claim(id, now));
        if (claim == null) {
            return false;
        }
        try {
            mailer.send(claim.email());
        } catch (MailException e) {
            if (claim.previousUntil() != null) {
                transactions.executeWithoutResult(status -> giveBack(id, claim));
            }
            throw e;
        }
        return true;
    }

    /** Claims the search's email if it is due: the email to send, or {@code null} when there is none. */
    private Claim claim(Long id, Instant now) {
        SavedOfferSearch search = searches.findById(id).orElse(null);
        if (search == null || search.getEmailFrequency() == SavedSearchEmails.OFF || !isDue(search, now)) {
            return null;
        }
        DocumentLanguage language = Objects.requireNonNullElse(search.getEmailLanguage(), DocumentLanguage.FR);
        String recipient = search.getFreelance().getEmail();
        if (search.getUnopenedSince() != null && !search.getUnopenedSince().plus(STOP_AFTER).isAfter(now)) {
            search.setEmailFrequency(SavedSearchEmails.OFF);
            search.setEmailsStoppedAt(now);
            log.info("Saved search {} stopped emailing: no offer opened from its emails in {} days", id, STOP_AFTER.toDays());
            return new Claim(composer.stopped(id, search.getName(), recipient, language, search.getEmailToken(), now), null, now);
        }
        Instant previous = Objects.requireNonNullElse(search.getEmailedUntil(), now);
        search.setEmailedUntil(now);
        OfferPageDto found = offerSearch.foundBetween(search.getFreelance().getId(), SavedOfferSearchService.filters(search), previous, now,
            OFFERS_PER_EMAIL);
        if (found.totalElements() == 0) {
            return null;
        }
        if (search.getUnopenedSince() == null) {
            search.setUnopenedSince(now);
        }
        return new Claim(composer.digest(new SavedSearchEmailComposer.Digest(id, search.getName(), recipient, language, search.getEmailToken(),
            found.content(), found.totalElements(), now)), previous, now);
    }

    /** Resend refused the email: its offers go to the next one. */
    private void giveBack(Long id, Claim claim) {
        searches.findById(id)
            .filter(search -> claim.cutoff().equals(search.getEmailedUntil()))
            .ifPresent(search -> search.setEmailedUntil(claim.previousUntil()));
    }

    /**
     * Whether a search's email is due: 30 minutes after the last for one sent at once, and once a day at its hour (Paris
     * time) for a digest.
     */
    static boolean isDue(SavedOfferSearch search, Instant now) {
        Instant until = search.getEmailedUntil();
        if (until == null) {
            return true;
        }
        if (search.getEmailFrequency() == SavedSearchEmails.INSTANT) {
            return !until.plus(GROUPED_EVERY).isAfter(now);
        }
        int hour = Objects.requireNonNullElse(search.getEmailHour(), DEFAULT_HOUR);
        ZonedDateTime local = now.atZone(PARIS);
        LocalDate day = local.getHour() < hour ? local.toLocalDate().minusDays(1) : local.toLocalDate();
        return until.isBefore(day.atTime(hour, 0).atZone(PARIS).toInstant());
    }

    private SavedOfferSearch byToken(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new ResourceNotFoundException(ErrorMessages.SAVED_SEARCH_EMAIL_LINK_INVALID);
        }
        return searches.findByEmailToken(token.strip())
            .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.SAVED_SEARCH_EMAIL_LINK_INVALID));
    }

    private static PublicSavedSearchEmailsDto toPublicDto(SavedOfferSearch search) {
        return new PublicSavedSearchEmailsDto(search.getName(), search.getEmailFrequency(),
            Objects.requireNonNullElse(search.getEmailLanguage(), DocumentLanguage.FR));
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
