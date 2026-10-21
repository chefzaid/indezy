package dev.swirlit.indezy.scraping.reader;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** What the readers of WordPress sites share: their REST API's times. */
final class WordPress {

    private WordPress() {
    }

    /** A {@code *_gmt} time of the REST API, which has no offset: it is UTC. {@code null} when unreadable. */
    static Instant utc(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
