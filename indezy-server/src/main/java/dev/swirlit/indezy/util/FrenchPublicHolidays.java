package dev.swirlit.indezy.util;

import dev.swirlit.indezy.model.enums.PublicHoliday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/** Public holidays of metropolitan France and the business days they leave in a month. */
public final class FrenchPublicHolidays {

    private FrenchPublicHolidays() {
    }

    /** The holidays of a year, by date. */
    public static SortedMap<LocalDate, PublicHoliday> of(int year) {
        LocalDate easter = easterSunday(year);
        SortedMap<LocalDate, PublicHoliday> holidays = new TreeMap<>();
        holidays.put(LocalDate.of(year, Month.JANUARY, 1), PublicHoliday.NEW_YEAR);
        holidays.put(easter.plusDays(1), PublicHoliday.EASTER_MONDAY);
        holidays.put(LocalDate.of(year, Month.MAY, 1), PublicHoliday.LABOUR_DAY);
        holidays.put(LocalDate.of(year, Month.MAY, 8), PublicHoliday.VICTORY_DAY);
        holidays.put(easter.plusDays(39), PublicHoliday.ASCENSION);
        holidays.put(easter.plusDays(50), PublicHoliday.WHIT_MONDAY);
        holidays.put(LocalDate.of(year, Month.JULY, 14), PublicHoliday.BASTILLE_DAY);
        holidays.put(LocalDate.of(year, Month.AUGUST, 15), PublicHoliday.ASSUMPTION);
        holidays.put(LocalDate.of(year, Month.NOVEMBER, 1), PublicHoliday.ALL_SAINTS);
        holidays.put(LocalDate.of(year, Month.NOVEMBER, 11), PublicHoliday.ARMISTICE);
        holidays.put(LocalDate.of(year, Month.DECEMBER, 25), PublicHoliday.CHRISTMAS);
        return holidays;
    }

    /** The holidays falling in a month, by date. */
    public static SortedMap<LocalDate, PublicHoliday> in(YearMonth month) {
        return new TreeMap<>(of(month.getYear()).subMap(month.atDay(1), month.atEndOfMonth().plusDays(1)));
    }

    /** Weekdays of the month that are not public holidays. */
    public static int businessDays(YearMonth month) {
        Map<LocalDate, PublicHoliday> holidays = in(month);
        return (int) month.atDay(1).datesUntil(month.atEndOfMonth().plusDays(1))
            .filter(date -> !isWeekend(date) && !holidays.containsKey(date))
            .count();
    }

    public static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /** Easter Sunday of the Gregorian calendar (anonymous Gregorian algorithm). */
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }
}
