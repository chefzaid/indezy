package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.BookingDtos;
import dev.swirlit.indezy.model.BookingPage;
import dev.swirlit.indezy.model.BookingWindow;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookingSlotsTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private static BookingPage page() {
        BookingPage page = new BookingPage();
        page.setDurationMinutes(45);
        page.setBufferMinutes(15);
        page.setMinNoticeHours(24);
        page.setMaxDaysAhead(16);
        page.setWindows(List.of(new BookingWindow(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(11, 0)),
            new BookingWindow(DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), LocalTime.of(15, 30))));
        return page;
    }

    private static Instant paris(String date, String time) {
        return LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(PARIS).toInstant();
    }

    @Test
    void offersEachCallLengthWithinTheHours_afterTheNotice_andLeavesHolidaysOut() {
        // Monday 26 October 2026 at 9:30: 24 hours of notice leave Tuesday's 9:00 out, not its 9:45.
        List<BookingDtos.Day> days = BookingSlots.free(page(), paris("2026-10-26", "09:30"), List.of());

        assertThat(days).extracting(BookingDtos.Day::date).containsExactly(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28),
            LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 10));
        // 10:30 would end after 11:00; 14:45 ends right at 15:30.
        assertThat(days.get(0).starts()).containsExactly(paris("2026-10-27", "09:45"));
        assertThat(days.get(1).starts()).containsExactly(paris("2026-10-28", "14:00"), paris("2026-10-28", "14:45"));
        assertThat(days.get(2).starts()).containsExactly(paris("2026-11-03", "09:00"), paris("2026-11-03", "09:45"));
        // Wednesday 11 November is Armistice Day, the last day ahead.
        assertThat(days).noneMatch(day -> day.date().equals(LocalDate.of(2026, 11, 11)));
    }

    @Test
    void keepsTheBufferAroundBusyTimes() {
        List<BookingSlots.Busy> interview = List.of(new BookingSlots.Busy(paris("2026-10-28", "14:50"), paris("2026-10-28", "15:50")));

        // With 15 minutes kept around each call, the interview takes both of Wednesday's starts.
        assertThat(BookingSlots.free(page(), paris("2026-10-26", "09:30"), interview))
            .noneMatch(day -> day.date().equals(LocalDate.of(2026, 10, 28)));
        BookingPage tight = page();
        tight.setBufferMinutes(0);
        assertThat(BookingSlots.free(tight, paris("2026-10-26", "09:30"), interview).get(1).starts())
            .containsExactly(paris("2026-10-28", "14:00"));
    }

    @Test
    void keepsTheHoursInParisTimeAcrossTheClockChange() {
        // Winter time starts on Sunday 25 October 2026: Tuesday 20 at 9:00 is 7:00 UTC, Tuesday 27 at 9:00 is 8:00 UTC.
        List<BookingDtos.Day> days = BookingSlots.free(page(), paris("2026-10-19", "08:00"), List.of());

        assertThat(days.get(0).date()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(days.get(0).starts().get(0)).isEqualTo(Instant.parse("2026-10-20T07:00:00Z"));
        assertThat(days.get(2).date()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(days.get(2).starts().get(0)).isEqualTo(Instant.parse("2026-10-27T08:00:00Z"));
    }
}
