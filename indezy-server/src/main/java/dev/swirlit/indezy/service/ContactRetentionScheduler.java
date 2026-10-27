package dev.swirlit.indezy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Anonymizes the dormant contacts every night at 03:45, Paris time: off in tests and in the one-off schema-migration
 * and seed runs. Two replicas clearing the same contacts write the same values, so no lock is needed.
 */
@Component
@ConditionalOnProperty(name = "indezy.contacts.retention-enabled", havingValue = "true")
@RequiredArgsConstructor
public class ContactRetentionScheduler {

    private final ContactRetentionService retention;

    @Scheduled(cron = "0 45 3 * * *", zone = "Europe/Paris")
    public void anonymizeDormant() {
        retention.anonymizeDormant(LocalDateTime.now());
    }
}
