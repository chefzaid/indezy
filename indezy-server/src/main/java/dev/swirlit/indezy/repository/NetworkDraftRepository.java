package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkDraft;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NetworkDraftRepository extends JpaRepository<NetworkDraft, Long> {

    List<NetworkDraft> findByAuthorIdOrderByCreatedAtDescIdDesc(Long authorId);

    Optional<NetworkDraft> findByIdAndAuthorId(Long id, Long authorId);

    long countByAuthorId(Long authorId);

    /** The scheduled drafts whose time came, the earliest first. */
    List<NetworkDraft> findByScheduledAtLessThanEqualOrderByScheduledAtAsc(Instant now, Pageable page);
}
