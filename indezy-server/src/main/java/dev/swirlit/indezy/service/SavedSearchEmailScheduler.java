package dev.swirlit.indezy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends the saved searches' due emails every {@code indezy.mail.tick} (5 minutes), with the offer collection that
 * finds their offers: off in tests and in the one-off schema-migration and seed runs.
 */
@Component
@ConditionalOnProperty(name = "indezy.offers.collection-enabled", havingValue = "true")
@RequiredArgsConstructor
public class SavedSearchEmailScheduler {

    private final SavedSearchEmailService emails;

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "${indezy.mail.tick:PT5M}")
    public void sendDue() {
        try {
            emails.sendDue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
