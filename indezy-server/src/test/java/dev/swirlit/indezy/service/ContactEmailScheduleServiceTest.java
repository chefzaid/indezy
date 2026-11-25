package dev.swirlit.indezy.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContactEmailScheduleServiceTest {

    @Test
    void spreadsTheEmailsEvenlyOverTheWorkingHoursOfTheNextWorkingDays() {
        // Friday 20 November 2026, 16:00 in Paris: an hour left today, then Monday and Tuesday.
        List<Instant> times = ContactEmailScheduleService.slots(Instant.parse("2026-11-20T15:00:00Z"), 5, 3, 50);
        assertThat(times).containsExactly(Instant.parse("2026-11-20T15:05:00Z"), Instant.parse("2026-11-20T15:32:30Z"),
            Instant.parse("2026-11-23T08:00:00Z"), Instant.parse("2026-11-23T12:00:00Z"), Instant.parse("2026-11-24T08:00:00Z"));
    }

    @Test
    void takesMoreDaysThanAskedUnderTheDailyCapAndSkipsHolidaysAndEvenings() {
        // Thursday 24 December 2026 at 18:00 in Paris: Christmas, then the weekend; Monday 28 is the first day.
        List<Instant> times = ContactEmailScheduleService.slots(Instant.parse("2026-12-24T17:00:00Z"), 3, 1, 2);
        assertThat(times).containsExactly(Instant.parse("2026-12-28T08:00:00Z"), Instant.parse("2026-12-28T12:00:00Z"),
            Instant.parse("2026-12-29T08:00:00Z"));
    }

    @Test
    void remindsFiveWorkingDaysLaterWithinWorkingHoursPastWeekendsAndHolidays() {
        java.time.ZoneId paris = java.time.ZoneId.of("Europe/Paris");
        // A Friday at 10:00: the next Friday at 10:00.
        assertThat(ContactEmailScheduleService.afterWorkingDays(java.time.LocalDateTime.of(2026, 11, 27, 10, 0).atZone(paris).toInstant(), 5))
            .isEqualTo(java.time.LocalDateTime.of(2026, 12, 4, 10, 0).atZone(paris).toInstant());
        // Christmas and New Year's Day skipped; an evening time brought within working hours.
        assertThat(ContactEmailScheduleService.afterWorkingDays(java.time.LocalDateTime.of(2026, 12, 24, 18, 30).atZone(paris).toInstant(), 5))
            .isEqualTo(java.time.LocalDateTime.of(2027, 1, 4, 16, 0).atZone(paris).toInstant());
    }

    /** A service whose transactions run at once, on mocks, at Friday 20 November 2026 16:00 in Paris. */
    private record Bench(ContactEmailScheduleService service, dev.swirlit.indezy.repository.ScheduledContactEmailRepository scheduled,
                         ContactEmailService emails) {
    }

    private static Bench bench() {
        dev.swirlit.indezy.repository.ScheduledContactEmailRepository scheduled =
            org.mockito.Mockito.mock(dev.swirlit.indezy.repository.ScheduledContactEmailRepository.class);
        dev.swirlit.indezy.repository.ContactInteractionRepository interactions =
            org.mockito.Mockito.mock(dev.swirlit.indezy.repository.ContactInteractionRepository.class);
        ContactEmailService emails = org.mockito.Mockito.mock(ContactEmailService.class);
        org.springframework.transaction.support.TransactionTemplate transaction = new org.springframework.transaction.support.TransactionTemplate(
            org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class));
        ContactEmailScheduleService service = new ContactEmailScheduleService(scheduled,
            org.mockito.Mockito.mock(dev.swirlit.indezy.repository.ContactRepository.class), interactions, emails,
            java.time.Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), java.time.ZoneOffset.UTC), transaction);
        return new Bench(service, scheduled, emails);
    }

    private static dev.swirlit.indezy.model.ScheduledContactEmail due(Bench bench, long id, int attempts) {
        dev.swirlit.indezy.model.Contact contact = new dev.swirlit.indezy.model.Contact();
        contact.setId(7L);
        dev.swirlit.indezy.model.ScheduledContactEmail email = new dev.swirlit.indezy.model.ScheduledContactEmail();
        email.setId(id);
        email.setContact(contact);
        email.setUserId(3L);
        email.setFreelanceId(9L);
        email.setSubject("Disponible");
        email.setBody("Bonjour,");
        email.setAttempts(attempts);
        email.setSendAt(Instant.parse("2026-11-20T14:00:00Z"));
        email.setClaimedAt(Instant.parse("2026-11-20T15:00:00Z"));
        org.mockito.Mockito.when(bench.scheduled().findDue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any())).thenReturn(List.of(id));
        org.mockito.Mockito.when(bench.scheduled().claim(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any())).thenReturn(1);
        org.mockito.Mockito.when(bench.scheduled().findById(id)).thenReturn(java.util.Optional.of(email));
        return email;
    }

    private static void failing(Bench bench, RuntimeException failure) {
        org.mockito.Mockito.when(bench.emails().send(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenThrow(failure);
    }

    @Test
    void triesARefusedEmailAgainOnTheNextWorkingMorningAndAFailedOneInAnHour() {
        Bench refused = bench();
        dev.swirlit.indezy.model.ScheduledContactEmail capped = due(refused, 1L, 0);
        failing(refused, new dev.swirlit.indezy.exception.InvalidStateException("Up to 50 emails a day"));
        refused.service().sendDue();
        // The day's emails all gone on a Friday: Monday 9:00 in Paris.
        assertThat(capped.getSendAt()).isEqualTo(Instant.parse("2026-11-23T08:00:00Z"));
        assertThat(capped.getAttempts()).isEqualTo(1);
        assertThat(capped.getClaimedAt()).isNull();

        Bench failed = bench();
        dev.swirlit.indezy.model.ScheduledContactEmail later = due(failed, 2L, 1);
        failing(failed, new dev.swirlit.indezy.mail.MailException("Resend answered 500"));
        failed.service().sendDue();
        assertThat(later.getSendAt()).isEqualTo(Instant.parse("2026-11-20T16:00:00Z"));
        assertThat(later.getAttempts()).isEqualTo(2);
    }

    @Test
    void dropsAnEmailAfterItsLastAttemptAndSkipsOneAnotherReplicaTook() {
        Bench last = bench();
        dev.swirlit.indezy.model.ScheduledContactEmail email = due(last, 3L, ContactEmailScheduleService.ATTEMPTS_MAX - 1);
        failing(last, new IllegalStateException("down"));
        last.service().sendDue();
        org.mockito.Mockito.verify(last.scheduled()).delete(email);

        Bench taken = bench();
        due(taken, 4L, 0);
        org.mockito.Mockito.when(taken.scheduled().claim(org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any())).thenReturn(0);
        taken.service().sendDue();
        org.mockito.Mockito.verifyNoInteractions(taken.emails());
    }
}
