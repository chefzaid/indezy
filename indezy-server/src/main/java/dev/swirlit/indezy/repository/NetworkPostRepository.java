package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface NetworkPostRepository extends JpaRepository<NetworkPost, Long> {

    /** The posts of these authors, the newest first. */
    Page<NetworkPost> findByAuthorIdInOrderByCreatedAtDescIdDesc(Collection<Long> authorIds, Pageable page);

    Optional<NetworkPost> findByIdAndAuthorId(Long id, Long authorId);
}
