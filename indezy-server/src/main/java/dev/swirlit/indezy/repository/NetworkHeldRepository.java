package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkHeld;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NetworkHeldRepository extends JpaRepository<NetworkHeld, Long> {

    /** What is held, the oldest first. */
    List<NetworkHeld> findAllByOrderByCreatedAtAscIdAsc(Pageable page);

    List<NetworkHeld> findByAuthorIdAndCreatedAtAfter(Long authorId, LocalDateTime since);

    long countBy();
}
