package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.UserPreferenceDefaults;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Archives the pipeline opportunities left idle for their account's inactivity period, for the accounts that asked for
 * it: the ones the dashboard and the Kanban board flag as stale, the same day count included.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectArchivingService {

    private final FreelanceRepository freelances;
    private final ProjectRepository projects;

    /** Archives every opted-in workspace's idle opportunities as of {@code now}; how many it archived. */
    @Transactional
    public int archiveIdle(LocalDateTime now) {
        int archived = 0;
        for (Object[] workspace : freelances.findAutoArchivingWorkspaces()) {
            Long freelanceId = (Long) workspace[0];
            int inactivityDays = workspace[1] != null ? (Integer) workspace[1] : UserPreferenceDefaults.INACTIVITY_DAYS;
            archived += projects.archiveIdle(freelanceId, now.minusDays(inactivityDays), now);
        }
        if (archived > 0) {
            log.info("Archived {} idle opportunities", archived);
        }
        return archived;
    }
}
