package dev.swirlit.indezy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends the weekly digests that are due every quarter of an hour, so a replica restarting on Monday morning still sends
 * them, and the emails of the calls booked through the booking link every minute: off in tests and in the one-off
 * schema-migration and seed runs, like the saved searches' emails.
 */
@Component
@ConditionalOnProperty(name = "indezy.offers.collection-enabled", havingValue = "true")
@RequiredArgsConstructor
public class WeeklyDigestScheduler {

    private final WeeklyDigestService digests;
    private final BookingNoticeService bookingNotices;

    @Scheduled(initialDelayString = "PT3M", fixedDelayString = "PT15M")
    public void sendDue() {
        try {
            digests.sendDue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Emails the calls booked through the booking link soon after, every minute. */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void sendBookingNotices() {
        try {
            bookingNotices.sendDue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
