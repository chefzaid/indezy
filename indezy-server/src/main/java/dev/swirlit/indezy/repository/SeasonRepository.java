package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Season;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, Long> {

    List<Season> findByFreelanceIdOrderByStartDateDesc(Long freelanceId);

    /** Owner workspace of the season, used by access checks without loading the entity graph. */
    @Query("SELECT s.freelance.id FROM Season s WHERE s.id = :id")
    Optional<Long> findOwnerFreelanceIdById(@Param("id") Long id);
}
