package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.BookingDtos;
import dev.swirlit.indezy.model.BookingPage;
import dev.swirlit.indezy.model.BookingWindow;
import dev.swirlit.indezy.util.FrenchPublicHolidays;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The free starts of a booking page (see docs/features.md#booking-link): every call length within each weekday's hours,
 * in Paris time, from the minimum notice to the last day ahead, leaving out French public holidays and any start whose
 * call, with the buffer on both sides, would meet a busy time (a call booked, an interview planned).
 */
final class BookingSlots {

    static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    /** A busy time: a booked call, or an interview step. */
    record Busy(Instant start, Instant end) {
    }

    private BookingSlots() {
    }

    static List<BookingDtos.Day> free(BookingPage page, Instant now, List<Busy> busy) {
        Duration length = Duration.ofMinutes(page.getDurationMinutes());
        Duration buffer = Duration.ofMinutes(page.getBufferMinutes());
        Instant earliest = now.plus(Duration.ofHours(page.getMinNoticeHours()));
        LocalDate today = LocalDate.ofInstant(now, PARIS);
        List<BookingDtos.Day> days = new ArrayList<>();
        for (LocalDate date = today; !date.isAfter(today.plusDays(page.getMaxDaysAhead())); date = date.plusDays(1)) {
            if (FrenchPublicHolidays.of(date.getYear()).containsKey(date)) {
                continue;
            }
            List<Instant> starts = new ArrayList<>();
            for (BookingWindow window : page.getWindows()) {
                if (window.getDayOfWeek() != date.getDayOfWeek()) {
                    continue;
                }
                for (LocalTime time = window.getStart(); !time.plus(length).isAfter(window.getEnd()) && !time.plus(length).isBefore(time);
                     time = time.plus(length)) {
                    Instant start = date.atTime(time).atZone(PARIS).toInstant();
                    Instant end = start.plus(length);
                    boolean taken = busy.stream().anyMatch(other -> start.minus(buffer).isBefore(other.end()) &&
                        other.start().isBefore(end.plus(buffer)));
                    if (!start.isBefore(earliest) && !taken) {
                        starts.add(start);
                    }
                }
            }
            if (!starts.isEmpty()) {
                days.add(new BookingDtos.Day(date, starts.stream().sorted().toList()));
            }
        }
        return days;
    }
}
