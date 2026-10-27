package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyDigestServiceTest {

    @Test
    void isDueFromMondayEightOClockParisTimeForTheWholeWeek() {
        // Monday 26 October 2026, Paris in winter time (UTC+1).
        assertThat(WeeklyDigestService.dueWeek(Instant.parse("2026-10-26T06:59:00Z"))).isNull();
        assertThat(WeeklyDigestService.dueWeek(Instant.parse("2026-10-26T07:00:00Z"))).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(WeeklyDigestService.dueWeek(Instant.parse("2026-11-01T22:59:00Z"))).isEqualTo(LocalDate.of(2026, 10, 26));
        // Sunday 23:30 UTC is already Monday in Paris, before eight.
        assertThat(WeeklyDigestService.dueWeek(Instant.parse("2026-11-01T23:30:00Z"))).isNull();
        // Summer time (UTC+2).
        assertThat(WeeklyDigestService.dueWeek(Instant.parse("2026-06-01T06:00:00Z"))).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void givesATokenOnceWhenTheDigestIsTurnedOn() {
        User user = new User();
        WeeklyDigestService.prepare(user);
        assertThat(user.getWeeklyDigestToken()).isNull();

        user.setWeeklyReports(true);
        WeeklyDigestService.prepare(user);
        String token = user.getWeeklyDigestToken();
        assertThat(token).hasSize(43);
        WeeklyDigestService.prepare(user);
        assertThat(user.getWeeklyDigestToken()).isEqualTo(token);
    }
}
