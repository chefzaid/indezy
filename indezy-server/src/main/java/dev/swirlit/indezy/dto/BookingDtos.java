package dev.swirlit.indezy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** The booking link's settings, what it shows a recruiter, and the calls booked through it. */
public final class BookingDtos {

    private BookingDtos() {
    }

    /** Hours of one weekday, in Paris time. */
    public record Window(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime start, @NotNull LocalTime end) {
    }

    /** The settings as written. */
    public record Settings(
        boolean enabled,
        @NotBlank @Size(max = 100) String title,
        @Min(15) @Max(120) int durationMinutes,
        @Min(0) @Max(60) int bufferMinutes,
        @Min(0) @Max(168) int minNoticeHours,
        @Min(1) @Max(60) int maxDaysAhead,
        @NotNull @Size(max = 7) List<@Valid Window> windows) {
        public Settings {
            windows = windows == null ? null : List.copyOf(windows);
        }
    }

    /** The settings as shown, with the link's slug (none until saved once). */
    public record Page(String slug, Settings settings) {
    }

    /** One day's free starts, in Paris time. */
    public record Day(LocalDate date, List<Instant> starts) {
        public Day {
            starts = List.copyOf(starts);
        }
    }

    /** What the link shows: who, how long a call lasts, and the free starts day by day. */
    public record PublicPage(String freelancer, String title, int durationMinutes, String timeZone, List<Day> days) {
        public PublicPage {
            days = List.copyOf(days);
        }
    }

    /** A recruiter's booking. */
    public record Request(
        @NotNull Instant start,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 200) String email,
        @Size(max = 100) String company,
        @Size(max = 500) String message) {
        @Override
        public String toString() {
            return "Request[start=" + start + "]";
        }
    }

    /** A call as the freelancer sees it. */
    public record Booking(Long id, Instant startsAt, Instant endsAt, String name, String email, String company, String message,
                          Instant cancelledAt) {
        @Override
        public String toString() {
            return "Booking[" + id + "]";
        }
    }

    /** What the recruiter is told once booked. */
    public record Confirmation(Instant startsAt, Instant endsAt, String freelancer) {
        @Override
        public String toString() {
            return "Confirmation[" + startsAt + "]";
        }
    }
}
