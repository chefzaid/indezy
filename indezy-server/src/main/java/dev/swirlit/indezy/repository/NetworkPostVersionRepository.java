package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkPostVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NetworkPostVersionRepository extends JpaRepository<NetworkPostVersion, Long> {

    /** A post's earlier texts, the latest first. */
    List<NetworkPostVersion> findByPostIdOrderByCreatedAtDescIdDesc(Long postId);

    @Modifying
    @Query("delete from NetworkPostVersion v where v.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
