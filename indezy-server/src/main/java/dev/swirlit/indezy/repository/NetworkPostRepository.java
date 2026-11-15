package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkPostRepository extends JpaRepository<NetworkPost, Long> {

    /** The posts of these authors, the newest first. */
    Page<NetworkPost> findByAuthorIdInOrderByCreatedAtDescIdDesc(Collection<Long> authorIds, Pageable page);

    Optional<NetworkPost> findByIdAndAuthorId(Long id, Long authorId);

    long countByAuthorIdAndCreatedAtAfter(Long authorId, LocalDateTime since);

    List<NetworkPost> findByRepostOfIdIn(Collection<Long> originalIds);

    /** The posts of these authors, or naming one of these tags but by the excluded authors, the newest first. */
    @Query(value = "select distinct p from NetworkPost p left join p.tags t where (p.authorId in :authors or t in :tags) " +
        "and p.authorId not in :excluded order by p.createdAt desc, p.id desc",
        countQuery = "select count(distinct p) from NetworkPost p left join p.tags t where (p.authorId in :authors or t in :tags) " +
            "and p.authorId not in :excluded")
    Page<NetworkPost> feed(@Param("authors") Collection<Long> authors, @Param("tags") Collection<String> tags,
                           @Param("excluded") Collection<Long> excluded, Pageable page);

    /** The posts naming one of these tags since then, with their tags. */
    @Query("select distinct p from NetworkPost p join fetch p.tags t where p.id in " +
        "(select q.id from NetworkPost q join q.tags u where u in :tags and q.createdAt >= :since)")
    List<NetworkPost> taggedSince(@Param("tags") Collection<String> tags, @Param("since") LocalDateTime since);

    List<NetworkPost> findByAuthorIdAndCreatedAtAfter(Long authorId, LocalDateTime since);
}
