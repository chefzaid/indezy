package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.AutopilotPack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AutopilotPackRepository extends JpaRepository<AutopilotPack, Long> {

    /** The workspace's packs prepared since {@code since}, newest first. */
    List<AutopilotPack> findByFreelanceIdAndCreatedAtAfterOrderByCreatedAtDesc(Long freelanceId, LocalDateTime since);

    boolean existsByFreelanceIdAndOfferId(Long freelanceId, Long offerId);

    Optional<AutopilotPack> findByIdAndFreelanceId(Long id, Long freelanceId);

    /** The packs older than {@code before}, gone. */
    @Modifying
    @Transactional
    @Query("DELETE FROM AutopilotPack p WHERE p.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
