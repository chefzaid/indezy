package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.PublicHoliday;

import java.time.LocalDate;

/** A French public holiday of the report's month; clients translate the holiday name. */
public record PublicHolidayDto(LocalDate date, PublicHoliday holiday) {
}
