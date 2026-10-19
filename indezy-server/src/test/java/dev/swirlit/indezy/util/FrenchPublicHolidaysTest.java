package dev.swirlit.indezy.util;

import dev.swirlit.indezy.model.enums.PublicHoliday;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class FrenchPublicHolidaysTest {

    @ParameterizedTest
    @CsvSource({"2024, 2024-03-31", "2025, 2025-04-20", "2026, 2026-04-05", "2027, 2027-03-28", "2038, 2038-04-25"})
    void easterSunday_ShouldFollowTheGregorianComputus(int year, LocalDate easter) {
        assertThat(FrenchPublicHolidays.easterSunday(year)).isEqualTo(easter);
    }

    @Test
    void of_ShouldListTheElevenHolidaysWithTheMovableOnesFromEaster() {
        Map<LocalDate, PublicHoliday> holidays = FrenchPublicHolidays.of(2026);

        assertThat(holidays).hasSize(11).containsExactly(
            entry(LocalDate.of(2026, 1, 1), PublicHoliday.NEW_YEAR),
            entry(LocalDate.of(2026, 4, 6), PublicHoliday.EASTER_MONDAY),
            entry(LocalDate.of(2026, 5, 1), PublicHoliday.LABOUR_DAY),
            entry(LocalDate.of(2026, 5, 8), PublicHoliday.VICTORY_DAY),
            entry(LocalDate.of(2026, 5, 14), PublicHoliday.ASCENSION),
            entry(LocalDate.of(2026, 5, 25), PublicHoliday.WHIT_MONDAY),
            entry(LocalDate.of(2026, 7, 14), PublicHoliday.BASTILLE_DAY),
            entry(LocalDate.of(2026, 8, 15), PublicHoliday.ASSUMPTION),
            entry(LocalDate.of(2026, 11, 1), PublicHoliday.ALL_SAINTS),
            entry(LocalDate.of(2026, 11, 11), PublicHoliday.ARMISTICE),
            entry(LocalDate.of(2026, 12, 25), PublicHoliday.CHRISTMAS));
    }

    @Test
    void in_ShouldKeepOnlyTheMonthsHolidaysIncludingItsLastDay() {
        assertThat(FrenchPublicHolidays.in(YearMonth.of(2026, 5))).containsOnlyKeys(
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 8), LocalDate.of(2026, 5, 14), LocalDate.of(2026, 5, 25));
        assertThat(FrenchPublicHolidays.in(YearMonth.of(2026, 12))).containsOnlyKeys(LocalDate.of(2026, 12, 25));
        assertThat(FrenchPublicHolidays.in(YearMonth.of(2026, 2))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        // 21 weekdays, Labour Day, Victory Day, Ascension and Whit Monday fall on weekdays
        "2026-05, 17",
        // 21 weekdays, All Saints' Day is a Sunday, Armistice a Wednesday
        "2026-11, 20",
        // 20 weekdays and no holiday
        "2026-02, 20",
        // 23 weekdays, Christmas is a Friday
        "2026-12, 22"
    })
    void businessDays_ShouldCountWeekdaysThatAreNotHolidays(String month, int businessDays) {
        assertThat(FrenchPublicHolidays.businessDays(YearMonth.parse(month))).isEqualTo(businessDays);
    }

    @Test
    void isWeekend_ShouldMatchSaturdayAndSundayOnly() {
        assertThat(FrenchPublicHolidays.isWeekend(LocalDate.of(2026, 10, 17))).isTrue();
        assertThat(FrenchPublicHolidays.isWeekend(LocalDate.of(2026, 10, 18))).isTrue();
        assertThat(FrenchPublicHolidays.isWeekend(LocalDate.of(2026, 10, 19))).isFalse();
    }
}
