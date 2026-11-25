package dev.swirlit.indezy.push;

import dev.swirlit.indezy.constants.UserPreferenceDefaults;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.PushSubscriptionRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.ContactInteractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tells the browsers that turned push on (see docs/features.md#web-push) of an interview starting within the hour and,
 * each weekday morning, of the follow-ups due. Each reminder is claimed in the database first, so several replicas send it
 * once. Interview times are the freelancer's, in Paris time. Each account is told in a transaction of its own: one that
 * fails is skipped and logged, the others still told, and the claims made for them kept (a single transaction would undo
 * them all, and push them again on the next run).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushReminders {

    /** An interview starting this soon is pushed. */
    static final Duration AHEAD = Duration.ofHours(1);
    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final PushService push;
    private final PushSubscriptionRepository subscriptions;
    private final InterviewStepRepository steps;
    private final UserRepository users;
    private final FreelanceRepository freelances;
    private final ContactInteractionService interactions;
    private final Clock clock;

    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 2 * 60 * 1000L)
    public void interviewsSoon() {
        if (push.available()) {
            interviews();
        }
    }

    private dev.swirlit.indezy.service.SavedOfferSearchService savedSearches;
    private org.springframework.transaction.support.TransactionTemplate isolated;

    /** Each account in a transaction of its own; without one, as in tests, each runs as it comes. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setTransactions(org.springframework.transaction.PlatformTransactionManager transactions) {
        this.isolated = new org.springframework.transaction.support.TransactionTemplate(transactions);
        this.isolated.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Whether {@code account} told its account: false when it told nothing or failed, the failure logged with {@code what}. */
    private boolean each(String what, Object id, java.util.function.BooleanSupplier account) {
        try {
            return isolated == null ? account.getAsBoolean() : Boolean.TRUE.equals(isolated.execute(status -> account.getAsBoolean()));
        } catch (RuntimeException e) {
            log.warn("The {} push of {} could not be made: {}", what, id, e.getMessage());
            return false;
        }
    }

    /** The saved searches whose new offers are pushed; without them, as in tests, none are. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSavedSearches(dev.swirlit.indezy.service.SavedOfferSearchService service) {
        this.savedSearches = service;
    }

    /** Every hour from 8:13 to 20:13, Paris time: the saved searches' new offers since the browsers were last told. */
    @Scheduled(cron = "0 13 8-20 * * *", zone = "Europe/Paris")
    public void newOffersFound() {
        if (push.available()) {
            newOffers();
        }
    }

    /**
     * Pushes each account with a browser whose saved searches have more new offers than when it was last told, once per
     * increase (fewer, as once the searches are opened, are only noted); how many accounts were told.
     */
    @Transactional
    public int newOffers() {
        dev.swirlit.indezy.service.SavedOfferSearchService searches = savedSearches;
        if (searches == null) {
            return 0;
        }
        int pushed = 0;
        for (Long userId : subscriptions.findUserIds()) {
            boolean told = each("new offers", userId, () -> {
                Optional<User> user = users.findById(userId);
                Optional<Long> freelanceId = user.flatMap(account -> freelances.findByEmail(account.getEmail())).map(Freelance::getId);
                if (freelanceId.isEmpty()) {
                    return false;
                }
                long total = searches.alerts(freelanceId.get()).newOffers();
                long previous = Optional.ofNullable(user.get().getOffersPushedTotal()).orElse(0L);
                if (total == previous || users.claimOffersPush(userId, previous, total) == 0 || total < previous) {
                    return false;
                }
                push.notify(PushEvent.OFFERS, List.of(userId), offersText(total), "/offers");
                return true;
            });
            pushed += told ? 1 : 0;
        }
        return pushed;
    }

    static Map<DocumentLanguage, String[]> offersText(long total) {
        return Map.of(DocumentLanguage.EN, new String[] {"New offers", total == 1 ? "1 new offer for your saved searches." :
                total + " new offers for your saved searches."},
            DocumentLanguage.FR, new String[] {"Nouvelles offres", total == 1 ? "1 nouvelle offre pour vos recherches enregistrées." :
                total + " nouvelles offres pour vos recherches enregistrées."});
    }

    @Scheduled(cron = "0 7 9 * * MON-FRI", zone = "Europe/Paris")
    public void followUpsDue() {
        if (push.available()) {
            followUps();
        }
    }

    /** Pushes the interviews starting within the hour, each once; how many were pushed. */
    @Transactional
    public int interviews() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(PARIS));
        int pushed = 0;
        for (InterviewStep step : steps.findRemindersDue(StepStatus.PLANNED, now, now.plus(AHEAD))) {
            boolean told = each("interview", step.getId(), () -> {
                Optional<User> user = users.findByEmail(step.getProject().getFreelance().getEmail());
                if (user.isEmpty() || subscriptions.findByUserId(user.get().getId()).isEmpty() || steps.claimReminder(step.getId(), clock.instant()) == 0) {
                    return false;
                }
                push.notify(PushEvent.INTERVIEWS, List.of(user.get().getId()), interviewText(step), "/projects/" + step.getProject().getId());
                return true;
            });
            pushed += told ? 1 : 0;
        }
        if (pushed > 0) {
            log.info("Pushed {} interview reminders", pushed);
        }
        return pushed;
    }

    /** Pushes each account with a browser to tell its follow-ups due, once a day; how many accounts were told. */
    @Transactional
    public int followUps() {
        LocalDate today = LocalDate.now(clock.withZone(PARIS));
        int pushed = 0;
        for (Long userId : subscriptions.findUserIds()) {
            boolean told = each("follow-ups", userId, () -> {
                Optional<Long> freelanceId = users.findById(userId).flatMap(user -> freelances.findByEmail(user.getEmail())).map(Freelance::getId);
                if (freelanceId.isEmpty()) {
                    return false;
                }
                int followUpDays = freelances.findFollowUpDays(freelanceId.get()).orElse(UserPreferenceDefaults.FOLLOW_UP_DAYS);
                int due = interactions.followUpsDue(freelanceId.get(), followUpDays, today).size();
                if (due == 0 || users.claimFollowUpsPush(userId, today) == 0) {
                    return false;
                }
                push.notify(PushEvent.FOLLOW_UPS, List.of(userId), followUpText(due), "/dashboard");
                return true;
            });
            pushed += told ? 1 : 0;
        }
        return pushed;
    }

    static Map<DocumentLanguage, String[]> interviewText(InterviewStep step) {
        Project project = step.getProject();
        String what = Optional.ofNullable(step.getTitle()).orElse("") + " · " + Optional.ofNullable(project.getRole()).orElse("") +
            (project.getClient() == null ? "" : " (" + project.getClient().getCompanyName() + ")");
        String at = step.getDate().format(TIME);
        return Map.of(DocumentLanguage.EN, new String[] {"Interview at " + at, what},
            DocumentLanguage.FR, new String[] {"Entretien à " + at, what});
    }

    static Map<DocumentLanguage, String[]> followUpText(int due) {
        return Map.of(DocumentLanguage.EN, new String[] {"Follow-ups due", due == 1 ? "1 contact waits for your follow-up." :
                due + " contacts wait for your follow-up."},
            DocumentLanguage.FR, new String[] {"Relances à faire", due == 1 ? "1 contact attend votre relance." :
                due + " contacts attendent votre relance."});
    }
}
