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
}
