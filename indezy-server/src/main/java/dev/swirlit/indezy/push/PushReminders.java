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
 * once. Interview times are the freelancer's, in Paris time.
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
            Optional<User> user = users.findByEmail(step.getProject().getFreelance().getEmail());
            if (user.isEmpty() || subscriptions.findByUserId(user.get().getId()).isEmpty() || steps.claimReminder(step.getId(), clock.instant()) == 0) {
                continue;
            }
            push.notify(PushEvent.INTERVIEWS, List.of(user.get().getId()), interviewText(step), "/projects/" + step.getProject().getId());
            pushed++;
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
            Optional<Long> freelanceId = users.findById(userId).flatMap(user -> freelances.findByEmail(user.getEmail())).map(Freelance::getId);
            if (freelanceId.isEmpty()) {
                continue;
            }
            int followUpDays = freelances.findFollowUpDays(freelanceId.get()).orElse(UserPreferenceDefaults.FOLLOW_UP_DAYS);
            int due = interactions.followUpsDue(freelanceId.get(), followUpDays, today).size();
            if (due == 0 || users.claimFollowUpsPush(userId, today) == 0) {
                continue;
            }
            push.notify(PushEvent.FOLLOW_UPS, List.of(userId), followUpText(due), "/dashboard");
            pushed++;
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
