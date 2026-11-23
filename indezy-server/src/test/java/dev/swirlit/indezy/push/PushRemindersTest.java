package dev.swirlit.indezy.push;

import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.PushSubscription;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.StepStatus;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.InterviewStepRepository;
import dev.swirlit.indezy.repository.PushSubscriptionRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.ContactInteractionService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushRemindersTest {

    // 13:30 in Paris.
    private static final Instant NOW = Instant.parse("2026-11-23T12:30:00Z");

    private final PushService push = mock(PushService.class);
    private final PushSubscriptionRepository subscriptions = mock(PushSubscriptionRepository.class);
    private final InterviewStepRepository steps = mock(InterviewStepRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final ContactInteractionService interactions = mock(ContactInteractionService.class);
    private final PushReminders reminders = new PushReminders(push, subscriptions, steps, users, freelances, interactions,
        Clock.fixed(NOW, ZoneId.of("UTC")));

    private static User user(long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private static InterviewStep step(long id, String email) {
        Freelance freelance = new Freelance();
        freelance.setEmail(email);
        Client client = new Client();
        client.setCompanyName("Acme");
        Project project = new Project();
        project.setId(40L + id);
        project.setRole("Lead Java");
        project.setClient(client);
        project.setFreelance(freelance);
        InterviewStep step = new InterviewStep();
        step.setId(id);
        step.setTitle("Entretien technique");
        step.setDate(LocalDateTime.of(2026, 11, 23, 14, 0));
        step.setProject(project);
        return step;
    }

    @Test
    void pushesEachInterviewStartingWithinTheHourOnceAndInParisTime() {
        when(steps.findRemindersDue(StepStatus.PLANNED, LocalDateTime.of(2026, 11, 23, 13, 30), LocalDateTime.of(2026, 11, 23, 14, 30)))
            .thenReturn(List.of(step(1, "alice@example.com"), step(2, "bob@example.com"), step(3, "carol@example.com")));
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user(7, "alice@example.com")));
        when(users.findByEmail("bob@example.com")).thenReturn(Optional.of(user(8, "bob@example.com")));
        when(users.findByEmail("carol@example.com")).thenReturn(Optional.of(user(9, "carol@example.com")));
        when(subscriptions.findByUserId(7L)).thenReturn(List.of(new PushSubscription()));
        when(subscriptions.findByUserId(8L)).thenReturn(List.of());
        when(subscriptions.findByUserId(9L)).thenReturn(List.of(new PushSubscription()));
        when(steps.claimReminder(1L, NOW)).thenReturn(1);
        // Another replica claimed Carol's.
        when(steps.claimReminder(3L, NOW)).thenReturn(0);

        assertThat(reminders.interviews()).isEqualTo(1);
        verify(push).notify(eq(PushEvent.INTERVIEWS), eq(List.of(7L)), anyMap(), eq("/projects/41"));
        verify(push, never()).notify(any(), eq(List.of(8L)), anyMap(), anyString());
        verify(steps, never()).claimReminder(eq(2L), any());
        assertThat(PushReminders.interviewText(step(1, "a")).get(DocumentLanguage.FR))
            .containsExactly("Entretien à 14:00", "Entretien technique · Lead Java (Acme)");
    }

    @Test
    void pushesTheFollowUpsDueOnceADay() {
        when(subscriptions.findUserIds()).thenReturn(List.of(7L, 8L));
        when(users.findById(7L)).thenReturn(Optional.of(user(7, "alice@example.com")));
        when(users.findById(8L)).thenReturn(Optional.of(user(8, "bob@example.com")));
        Freelance alice = new Freelance();
        alice.setId(70L);
        Freelance bob = new Freelance();
        bob.setId(80L);
        when(freelances.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(freelances.findByEmail("bob@example.com")).thenReturn(Optional.of(bob));
        when(freelances.findFollowUpDays(70L)).thenReturn(Optional.of(5));
        when(freelances.findFollowUpDays(80L)).thenReturn(Optional.empty());
        LocalDate today = LocalDate.of(2026, 11, 23);
        when(interactions.followUpsDue(70L, 5, today)).thenReturn(List.of(new DashboardStatsDto.FollowUpDue(), new DashboardStatsDto.FollowUpDue()));
        when(interactions.followUpsDue(80L, 5, today)).thenReturn(List.of());
        when(users.claimFollowUpsPush(7L, today)).thenReturn(1);

        assertThat(reminders.followUps()).isEqualTo(1);
        verify(push).notify(eq(PushEvent.FOLLOW_UPS), eq(List.of(7L)), anyMap(), eq("/dashboard"));
        assertThat(PushReminders.followUpText(2).get(DocumentLanguage.FR)[1]).isEqualTo("2 contacts attendent votre relance.");
        verify(users, never()).claimFollowUpsPush(eq(8L), any());
        assertThat(PushReminders.followUpText(1).get(DocumentLanguage.EN)[1]).isEqualTo("1 contact waits for your follow-up.");
    }

    @Test
    void doesNothingWithoutTheVapidKeys() {
        when(push.available()).thenReturn(false);
        reminders.interviewsSoon();
        reminders.followUpsDue();
        verify(steps, never()).findRemindersDue(any(), any(), any());
        verify(subscriptions, never()).findUserIds();
        assertThat(Map.of()).isEmpty();
    }
}
