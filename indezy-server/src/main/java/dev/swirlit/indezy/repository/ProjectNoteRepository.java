package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ProjectNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectNoteRepository extends JpaRepository<ProjectNote, Long> {

    /** Notes for a project, newest first, to render the journal timeline. */
    List<ProjectNote> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
