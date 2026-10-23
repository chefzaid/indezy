package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.SavedOfferSearch;
import dev.swirlit.indezy.model.enums.SavedSearchEmails;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** When a saved search's email is due: 30 minutes after the last one, or once a day at its hour, Paris time. */
class SavedSearchEmailServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T06:30:00Z");

    @Test
    void groupsTheOffersSentAtOnceEveryThirtyMinutes() {
        assertThat(SavedSearchEmailService.isDue(search(SavedSearchEmails.INSTANT, null, null), NOW)).isTrue();
        assertThat(SavedSearchEmailService.isDue(search(SavedSearchEmails.INSTANT, null, NOW.minus(Duration.ofMinutes(29))), NOW)).isFalse();
        assertThat(SavedSearchEmailService.isDue(search(SavedSearchEmails.INSTANT, null, NOW.minus(Duration.ofMinutes(30))), NOW)).isTrue();
    }

    @Test
    void sendsADigestOnceADayAtItsHourParisTime() {
        // 08:30 in Paris (summer time): today's 08:00 email is due once, until tomorrow's.
        assertThat(SavedSearchEmailService.isDue(search(SavedSearchEmails.DAILY, 8, Instant.parse("2026-10-22T07:00:00Z")), NOW)).isTrue();
        assertThat(SavedSearchEmailService.isDue(search(SavedSearchEmails.DAILY, 8, Instant.parse("2026-10-23T06:10:00Z")), NOW)).isFalse();
        // 07:30 in Paris: yesterday's was sent, today's is not due yet.
        assertThat(SavedSearchEmailService.isDue(search(SavedSearchEmails.DAILY, 8, Instant.parse("2026-10-22T07:00:00Z")),
            Instant.parse("2026-10-23T05:30:00Z"))).isFalse();
        // Without an hour, at 8; after the change to winter time, 08:00 in Paris is 07:00 UTC.
        assertThat(SavedSearchEmailService.isDue(search(SavedSearchEmails.DAILY, null, Instant.parse("2026-10-25T07:00:00Z")),
            Instant.parse("2026-10-26T06:59:00Z"))).isFalse();
        assertThat(SavedSearchEmailService.isDue(search(SavedSearchEmails.DAILY, null, Instant.parse("2026-10-25T07:00:00Z")),
            Instant.parse("2026-10-26T07:00:00Z"))).isTrue();
    }

    private static SavedOfferSearch search(SavedSearchEmails frequency, Integer hour, Instant emailedUntil) {
        SavedOfferSearch search = new SavedOfferSearch();
        search.setEmailFrequency(frequency);
        search.setEmailHour(hour);
        search.setEmailedUntil(emailedUntil);
        return search;
    }
}
