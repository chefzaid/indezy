package dev.swirlit.indezy.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** The hours of one weekday a booking page offers, in Paris time. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class BookingWindow {

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "starts_at", nullable = false)
    private LocalTime start;

    @Column(name = "ends_at", nullable = false)
    private LocalTime end;
}
