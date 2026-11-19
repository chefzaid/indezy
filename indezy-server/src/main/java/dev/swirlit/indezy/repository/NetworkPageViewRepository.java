package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkPageView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NetworkPageViewRepository extends JpaRepository<NetworkPageView, Long> {

    Optional<NetworkPageView> findByViewerIdAndMemberIdAndViewedOn(Long viewerId, Long memberId, LocalDate viewedOn);

    /** The views of a member's page since a time, the latest first. */
    List<NetworkPageView> findByMemberIdAndViewedAtAfterOrderByViewedAtDesc(Long memberId, LocalDateTime since);

    @Modifying
    @Query("delete from NetworkPageView v where v.viewedAt < :before")
    int deleteViewedBefore(@Param("before") LocalDateTime before);
}
