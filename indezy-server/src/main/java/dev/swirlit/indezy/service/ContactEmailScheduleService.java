package dev.swirlit.indezy.service;

import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.ScheduledContactEmail;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.ScheduledContactEmailRepository;
import dev.swirlit.indezy.util.FrenchPublicHolidays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Emails to contacts sent later from Indezy, spread over working days (see docs/features.md#emails-sent-from-indezy):
 * Monday to Friday but public holidays, 9:00 to 17:00 in Paris, a daily cap, evenly apart within a day. Each goes as
 * {@link ContactEmailService#send} sends it, recorded in the contact's exchanges then; one whose contact was in touch since
 * it was planned, or asked not to be contacted, is dropped unsent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactEmailScheduleService {

    static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    static final LocalTime OPENS = LocalTime.of(9, 0);
    static final LocalTime CLOSES = LocalTime.of(17, 0);
    /** The first email of a plan made today goes this long after it at the earliest. */
    static final Duration FIRST_DELAY = Duration.ofMinutes(5);
    static final int DAYS_MAX = 10;
    /** A workspace keeps at most this many emails waiting. */
    static final int WAITING_MAX = 200;
    static final int ATTEMPTS_MAX = 5;
    /** A replica that took an email this long ago and did not send it stopped: another may take it. */
    private static final Duration STALE = Duration.ofMinutes(10);
    private static final Duration RETRY = Duration.ofHours(1);
    private static final int BATCH = 20;

    private final ScheduledContactEmailRepository scheduled;
    private final ContactRepository contacts;
    private final ContactInteractionRepository interactions;
    private final ContactEmailService emails;
    private final Clock clock;
    private final TransactionTemplate transaction;

    /** One email to write: to whom, its subject and its message, written for that contact. */
    public record Draft(Long contactId, String subject, String body) {

        // Spring MVC logs bodies through toString at DEBUG: never the subject or the message.
        @Override
        public String toString() {
            return "Draft[contactId=" + contactId + "]";
        }
    }

    /** The plan: the emails, sent over this many working days, at most this many a day. */
    public record Plan(List<Draft> emails, boolean dossier, DocumentLanguage language, int days, int dailyMax) {

        public Plan {
            emails = List.copyOf(emails);
        }
    }

    /** An email waiting to go: to whom, about what, and when. */
    public record Waiting(Long id, Long contactId, String name, String company, String subject, Instant sendAt) {
    }

    /** Plans the emails, the first soonest; the times they go, in order. */
    @Transactional
    public List<Waiting> schedule(Long userId, Long freelanceId, Plan plan) {
        if (plan.days() < 1 || plan.days() > DAYS_MAX || plan.dailyMax() < 1 || plan.dailyMax() > ContactEmailService.DAILY_MAX) {
            throw new IllegalArgumentException("Emails are spread over 1 to " + DAYS_MAX + " working days, 1 to " + ContactEmailService.DAILY_MAX + " a day");
        }
        if (scheduled.countByFreelanceId(freelanceId) + plan.emails().size() > WAITING_MAX) {
            throw new InvalidStateException("Up to " + WAITING_MAX + " emails wait to go at a time");
        }
        List<Instant> times = slots(clock.instant(), plan.emails().size(), plan.days(), plan.dailyMax());
        List<ScheduledContactEmail> saved = new ArrayList<>();
        for (int index = 0; index < plan.emails().size(); index++) {
            Draft draft = plan.emails().get(index);
            Contact contact = contacts.findById(draft.contactId()).filter(found -> found.getFreelance().getId().equals(freelanceId))
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found with id: " + draft.contactId()));
            ScheduledContactEmail email = new ScheduledContactEmail();
            email.setFreelanceId(freelanceId);
            email.setUserId(userId);
            email.setContact(contact);
            email.setSubject(draft.subject().strip());
            email.setBody(draft.body());
            email.setDossier(plan.dossier());
            email.setLanguage(plan.language());
            email.setSendAt(times.get(index));
            saved.add(scheduled.save(email));
        }
        return saved.stream().map(ContactEmailScheduleService::toWaiting).toList();
    }

    @Transactional(readOnly = true)
    public List<Waiting> waiting(Long freelanceId) {
        return scheduled.findWaiting(freelanceId).stream().map(ContactEmailScheduleService::toWaiting).toList();
    }

    @Transactional
    public void cancel(Long freelanceId, Long id) {
        scheduled.delete(scheduled.findByIdAndFreelanceId(id, freelanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Scheduled email not found with id: " + id)));
    }

    /** Sends, every minute, the emails whose time came, each taken by one replica only. */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void sendDue() {
        Instant now = clock.instant();
        for (Long id : scheduled.findDue(now, now.minus(STALE), PageRequest.of(0, BATCH))) {
            Integer taken = transaction.execute(status -> scheduled.claim(id, now, now.minus(STALE)));
            if (taken == null || taken == 0) {
                continue;
            }
            try {
                transaction.executeWithoutResult(status -> send(id));
            } catch (RuntimeException e) {
                log.debug("Scheduled email {} not sent: {}", id, e.getClass().getSimpleName());
                transaction.executeWithoutResult(status -> retry(id, e instanceof InvalidStateException));
            }
        }
    }

    private void send(Long id) {
        ScheduledContactEmail email = scheduled.findById(id).orElse(null);
        if (email == null) {
            return;
        }
        Contact contact = email.getContact();
        boolean inTouch = interactions.findByContactIdOrderByOccurredOnDescIdDesc(contact.getId()).stream()
            .map(ContactInteraction::getCreatedAt).anyMatch(created -> created != null && created.isAfter(email.getCreatedAt()));
        if (!Boolean.TRUE.equals(contact.getDoNotContact()) && !inTouch && contact.getAnonymizedAt() == null) {
            emails.send(email.getUserId(), email.getFreelanceId(), contact.getId(), new ContactEmailService.Message(null, email.getSubject(),
                email.getBody(), false, false, email.getLanguage(), email.isDossier()));
        }
        scheduled.delete(email);
    }

    /** Tries again later: the next working morning when Indezy refused it (the day's emails all gone), in an hour otherwise. */
    private void retry(Long id, boolean refused) {
        scheduled.findById(id).ifPresent(email -> {
            if (email.getAttempts() + 1 >= ATTEMPTS_MAX) {
                scheduled.delete(email);
                return;
            }
            email.setAttempts(email.getAttempts() + 1);
            email.setClaimedAt(null);
            Instant now = clock.instant();
            email.setSendAt(refused ? nextMorning(now) : now.plus(RETRY));
        });
    }

    /**
     * The times {@code count} emails go from {@code now}: over {@code days} working days at most from the first, as
     * evenly as the daily cap allows (more days when it does not), within working hours, the first of today not before
     * {@link #FIRST_DELAY}.
     */
    static List<Instant> slots(Instant now, int count, int days, int dailyMax) {
        int perDay = Math.min(dailyMax, Math.max(1, (count + days - 1) / days));
        List<Instant> times = new ArrayList<>();
        ZonedDateTime earliest = now.plus(FIRST_DELAY).atZone(PARIS);
        LocalDate day = earliest.toLocalDate();
        while (times.size() < count) {
            if (working(day)) {
                ZonedDateTime opens = day.atTime(OPENS).atZone(PARIS);
                ZonedDateTime start = opens.isAfter(earliest) ? opens : earliest;
                ZonedDateTime end = day.atTime(CLOSES).atZone(PARIS);
                if (start.isBefore(end)) {
                    int today = Math.min(perDay, count - times.size());
                    long gap = Duration.between(start, end).toSeconds() / today;
                    for (int index = 0; index < today; index++) {
                        times.add(start.plusSeconds(gap * index).withNano(0).toInstant());
                    }
                }
            }
            day = day.plusDays(1);
        }
        return times;
    }

    private static Instant nextMorning(Instant now) {
        LocalDate day = now.atZone(PARIS).toLocalDate().plusDays(1);
        while (!working(day)) {
            day = day.plusDays(1);
        }
        return day.atTime(OPENS).atZone(PARIS).toInstant();
    }

    private static boolean working(LocalDate day) {
        return !FrenchPublicHolidays.isWeekend(day) && !FrenchPublicHolidays.of(day.getYear()).containsKey(day);
    }

    private static Waiting toWaiting(ScheduledContactEmail email) {
        Contact contact = email.getContact();
        return new Waiting(email.getId(), contact.getId(), contact.getFullName(),
            contact.getClient() == null ? null : contact.getClient().getCompanyName(), email.getSubject(), email.getSendAt());
    }
}
