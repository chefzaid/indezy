package dev.swirlit.indezy.scraping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FrenchDatesTest {

    private static final LocalDate REFERENCE = LocalDate.of(2026, 10, 19);

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "5 octobre 2026              | 2026-10-05",
        "Publiée le 19 oct. 2026      | 2026-10-19",
        "mer. 14 oct. 2026            | 2026-10-14",
        "mercredi 14 octobre 2026     | 2026-10-14",
        "1er décembre 2026            | 2026-12-01",
        "Décembre 2026                | 2026-12-01",
        "19/10/2026                   | 2026-10-19",
        "Démarrage : 01-11-2026       | 2026-11-01",
        "2026-11-02T00:00:00          | 2026-11-02",
        "16 oct                       | 2026-10-16",
        "5 janv.                      | 2027-01-05",
        "décembre                     | 2026-12-01",
        "mar. 14                      | -",
        "29 février                   | -",
        "31 février 2026              | -",
        "Dès que possible             | -",
        "                             | -"
    })
    void readsTheDatesJobBoardsWrite(String text, LocalDate expected) {
        assertThat(FrenchDates.parse(text, REFERENCE)).isEqualTo(expected);
    }

    @Test
    void startsTheDayInFrance() {
        assertThat(FrenchDates.startOfDay(LocalDate.of(2026, 10, 19))).isEqualTo(Instant.parse("2026-10-18T22:00:00Z"));
        assertThat(FrenchDates.startOfDay(LocalDate.of(2026, 1, 5))).isEqualTo(Instant.parse("2026-01-04T23:00:00Z"));
        assertThat(FrenchDates.startOfDay(null)).isNull();
    }
}
