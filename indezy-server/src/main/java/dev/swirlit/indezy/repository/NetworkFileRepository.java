package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NetworkFileRepository extends JpaRepository<NetworkFile, Long> {

    List<NetworkFile> findByPostIdInOrderByPositionAscIdAsc(Collection<Long> postIds);

    List<NetworkFile> findByMessageIdInOrderByPositionAscIdAsc(Collection<Long> messageIds);

    List<NetworkFile> findByHeldIdOrderByPositionAscIdAsc(Long heldId);

    long countByOwnerIdAndCreatedAtAfter(Long ownerId, LocalDateTime since);

    /** Files uploaded before the date and never attached: dropped by the nightly clean-up. */
    @Query("SELECT f.id FROM NetworkFile f WHERE f.postId IS NULL AND f.messageId IS NULL AND f.heldId IS NULL AND f.createdAt < :before")
    List<Long> unattachedBefore(@Param("before") LocalDateTime before);

    @Modifying
    @Query("DELETE FROM NetworkFile f WHERE f.id IN :ids")
    void deleteByIdIn(@Param("ids") Collection<Long> ids);
}
