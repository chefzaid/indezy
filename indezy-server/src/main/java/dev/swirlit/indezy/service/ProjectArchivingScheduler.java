package dev.swirlit.indezy.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Archives idle opportunities every night at 03:30, Paris time: off in tests and in the one-off schema-migration and
 * seed runs. Two replicas archiving the same opportunities set the same column, so no lock is needed.
 */
@Component
@ConditionalOnProperty(name = "indezy.projects.archiving-enabled", havingValue = "true")
@RequiredArgsConstructor
public class ProjectArchivingScheduler {

    private final ProjectArchivingService archiving;

    @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Paris")
    public void archiveIdle() {
        archiving.archiveIdle(LocalDateTime.now());
    }
}
