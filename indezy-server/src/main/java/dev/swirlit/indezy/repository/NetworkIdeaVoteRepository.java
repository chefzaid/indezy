package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkIdeaVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NetworkIdeaVoteRepository extends JpaRepository<NetworkIdeaVote, Long> {

    Optional<NetworkIdeaVote> findByIdeaIdAndUserId(Long ideaId, Long userId);

    List<NetworkIdeaVote> findByIdeaId(Long ideaId);

    long countByIdeaId(Long ideaId);

    /** The ideas a member voted for. */
    @Query("select v.ideaId from NetworkIdeaVote v where v.userId = :userId")
    List<Long> ideasOf(@Param("userId") Long userId);

    /** Each idea's votes, as (idea id, count) pairs. */
    @Query("select v.ideaId, count(v) from NetworkIdeaVote v group by v.ideaId")
    List<Object[]> counts();

    @Modifying
    @Query("delete from NetworkIdeaVote v where v.ideaId = :ideaId")
    void deleteByIdeaId(@Param("ideaId") Long ideaId);
}
