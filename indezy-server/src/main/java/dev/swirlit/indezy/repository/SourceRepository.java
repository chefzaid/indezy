package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Source;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SourceRepository extends JpaRepository<Source, Long> {

    List<Source> findByFreelanceId(Long freelanceId);

    /** Owner workspace of the source, used by access checks without loading the entity graph. */
    @Query("SELECT s.freelance.id FROM Source s WHERE s.id = :id")
    Optional<Long> findOwnerFreelanceIdById(@Param("id") Long id);
}
