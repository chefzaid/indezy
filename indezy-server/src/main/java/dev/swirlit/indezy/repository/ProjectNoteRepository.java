package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ProjectNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectNoteRepository extends JpaRepository<ProjectNote, Long> {

    /** Notes for a project, newest first, to render the journal timeline. */
    List<ProjectNote> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    /** Removes a project's journal before the project itself is deleted. */
    @Modifying
    @Query("DELETE FROM ProjectNote n WHERE n.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);
}
