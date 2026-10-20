package dev.swirlit.indezy.service;

import dev.swirlit.indezy.repository.FreelanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Gives the workspaces created before the job boards were sources their boards, once, when the server is ready. */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobBoardSourcesBackfill {

    private final FreelanceRepository freelances;
    private final JobBoardSources jobBoardSources;

    @EventListener(ApplicationReadyEvent.class)
    public void seedExistingWorkspaces() {
        int added = 0;
        for (Long freelanceId : freelances.findIdsWithoutJobBoardSources()) {
            added += jobBoardSources.seed(freelanceId);
        }
        if (added > 0) {
            log.info("Added {} job board sources to existing workspaces", added);
        }
    }
}
