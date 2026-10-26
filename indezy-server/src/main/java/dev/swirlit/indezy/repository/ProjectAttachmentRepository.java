package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ProjectAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProjectAttachmentRepository extends JpaRepository<ProjectAttachment, Long> {

    /** What an opportunity's files are, newest first, without reading their content. */
    interface Summary {
        Long getId();

        String getFileName();

        String getContentType();

        long getSizeBytes();

        LocalDateTime getCreatedAt();
    }

    @Query("SELECT a.id AS id, a.fileName AS fileName, a.contentType AS contentType, a.sizeBytes AS sizeBytes, " +
        "a.createdAt AS createdAt FROM ProjectAttachment a WHERE a.project.id = :projectId ORDER BY a.createdAt DESC, a.id DESC")
    List<Summary> findSummaries(@Param("projectId") Long projectId);

    /** The workspaces' files, for its data export, without their content. */
    @Query("SELECT a.id AS id, a.fileName AS fileName, a.contentType AS contentType, a.sizeBytes AS sizeBytes, " +
        "a.createdAt AS createdAt FROM ProjectAttachment a WHERE a.project.freelance.id = :freelanceId ORDER BY a.id")
    List<Summary> findSummariesByFreelanceId(@Param("freelanceId") Long freelanceId);

    long countByProjectId(Long projectId);

    @Query("SELECT a.project.freelance.id FROM ProjectAttachment a WHERE a.id = :id")
    Optional<Long> findOwnerFreelanceIdById(@Param("id") Long id);

    /** Deletes an opportunity's files without reading their content; files already loaded are forgotten. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ProjectAttachment a WHERE a.project.id = :projectId")
    int deleteByProjectId(@Param("projectId") Long projectId);
}
