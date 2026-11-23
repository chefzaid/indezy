package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.AutopilotDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AutopilotDraftRepository extends JpaRepository<AutopilotDraft, Long> {

    /** The workspace's drafts written since {@code since}, newest first. */
    List<AutopilotDraft> findByFreelanceIdAndCreatedAtAfterOrderByCreatedAtDesc(Long freelanceId, LocalDateTime since);

    boolean existsByFreelanceIdAndContactIdAndCreatedAtAfter(Long freelanceId, Long contactId, LocalDateTime since);

    Optional<AutopilotDraft> findByIdAndFreelanceId(Long id, Long freelanceId);

    /** The drafts older than {@code before}, gone. */
    @Modifying
    @Transactional
    @Query("DELETE FROM AutopilotDraft d WHERE d.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
