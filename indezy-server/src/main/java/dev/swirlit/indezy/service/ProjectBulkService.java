package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ProjectBulkUpdateRequest;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.Source;
import dev.swirlit.indezy.model.enums.ProjectStatus;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.SourceRepository;
import dev.swirlit.indezy.util.SkillNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** One change applied to several opportunities at once, from the Kanban board's or the opportunity list's selection. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ProjectBulkService {

    private final ProjectRepository projectRepository;
    private final SourceRepository sourceRepository;
    private final ProjectService projectService;

    /**
     * Applies {@code request} in one transaction: a new status (and lost reason), a source, a tag added to each tech
     * stack, the archive, or deleting them. The caller has checked that they and the source belong to its workspace.
     * Returns how many were changed or deleted.
     */
    public int apply(ProjectBulkUpdateRequest request) {
        ProjectBulkUpdateRequest.Action action = request.action();
        if (action == ProjectBulkUpdateRequest.Action.STATUS && request.status() == null) {
            throw new IllegalArgumentException("A status is required to change the status");
        }
        if (action == ProjectBulkUpdateRequest.Action.SOURCE && request.sourceId() == null) {
            throw new IllegalArgumentException("A source is required to assign a source");
        }
        String tag = request.tag() == null ? "" : request.tag().strip();
        if (action == ProjectBulkUpdateRequest.Action.TAG && (tag.isEmpty() || tag.contains(","))) {
            throw new IllegalArgumentException("A tag without commas is required to add a tag");
        }
        if (action == ProjectBulkUpdateRequest.Action.TAG) {
            return addTag(request.projectIds(), tag);
        }
        if (action == ProjectBulkUpdateRequest.Action.DELETE) {
            // One by one, so each goes with its notes, files and activity reports as a single delete does.
            request.projectIds().forEach(projectService::delete);
            log.info("Bulk DELETE of {} projects", request.projectIds().size());
            return request.projectIds().size();
        }
        Source source = action == ProjectBulkUpdateRequest.Action.SOURCE ?
            sourceRepository.findById(request.sourceId()).orElseThrow(() ->
                new ResourceNotFoundException(String.format("Source not found with id: %d", request.sourceId()))) : null;
        LocalDateTime now = LocalDateTime.now();
        Consumer<Project> change = switch (action) {
            case STATUS -> project -> {
                project.setStatus(request.status());
                project.setLostReason(ProjectStatus.LOST.equals(request.status()) ? request.lostReason() : null);
            };
            case SOURCE -> project -> project.setSource(source);
            case ARCHIVE -> project -> {
                if (project.getArchivedAt() == null) {
                    project.setArchivedAt(now);
                }
            };
            case TAG, DELETE -> throw new IllegalStateException(action + " is applied above");
        };
        List<Project> projects = projectRepository.findAllById(request.projectIds());
        Map<Long, ProjectStatus> before = new HashMap<>();
        projects.forEach(project -> before.put(project.getId(), project.getStatus()));
        projects.forEach(change);
        projectRepository.saveAll(projects);
        projects.forEach(project -> projectService.publishStatusChange(project, before.get(project.getId())));
        log.info("Bulk {} on {} projects", action, projects.size());
        return projects.size();
    }

    /** Adds {@code tag} to the tech stacks that lack it and have room for it; returns how many changed. */
    private int addTag(List<Long> projectIds, String tag) {
        int changed = 0;
        for (Project project : projectRepository.findAllById(projectIds)) {
            String tagged = withTag(project.getTechStack(), tag);
            if (!tagged.equals(project.getTechStack()) && tagged.length() <= Project.TECH_STACK_MAX) {
                project.setTechStack(tagged);
                changed++;
            }
        }
        log.info("Bulk TAG on {} projects", changed);
        return changed;
    }

    /** {@code techStack} ("Java, Spring") with {@code tag} at its end, unless it already names that skill, however spelled. */
    static String withTag(String techStack, String tag) {
        List<String> tags = new ArrayList<>(Arrays.stream(techStack == null ? new String[0] : techStack.split(","))
            .map(String::strip).filter(existing -> !existing.isEmpty()).toList());
        if (tags.stream().noneMatch(existing -> SkillNames.same(existing, tag))) {
            tags.add(tag);
        }
        return String.join(", ", tags);
    }
}
