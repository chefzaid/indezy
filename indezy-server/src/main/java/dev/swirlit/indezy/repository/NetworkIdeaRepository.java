package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkIdea;
import dev.swirlit.indezy.model.enums.NetworkIdeaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NetworkIdeaRepository extends JpaRepository<NetworkIdea, Long> {

    /** The board: its latest 2,000 ideas but those of a status (the duplicates merged), the newest first. */
    List<NetworkIdea> findTop2000ByStatusNotOrderByCreatedAtDescIdDesc(NetworkIdeaStatus status);

    /** How many ideas a member proposed since a moment: the daily limit. */
    long countByAuthorIdAndCreatedAtAfter(Long authorId, LocalDateTime since);
}
