package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.NetworkPost;
import dev.swirlit.indezy.model.enums.NetworkPostKind;
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

    /** The posts whose text holds what was searched (a LIKE pattern on the lower-case text) but the excluded authors', the newest first. */
    @Query(value = "select p from NetworkPost p where lower(p.text) like :pattern escape '\\' and p.authorId not in :excluded " +
        "order by p.createdAt desc, p.id desc",
        countQuery = "select count(p) from NetworkPost p where lower(p.text) like :pattern escape '\\' and p.authorId not in :excluded")
    Page<NetworkPost> search(@Param("pattern") String pattern, @Param("excluded") Collection<Long> excluded, Pageable page);

    /** The posts of a kind (the missions passed on, say) but those of the excluded authors, the newest first. */
    @Query(value = "select p from NetworkPost p where p.kind = :kind and p.authorId not in :excluded order by p.createdAt desc, p.id desc",
        countQuery = "select count(p) from NetworkPost p where p.kind = :kind and p.authorId not in :excluded")
    Page<NetworkPost> ofKind(@Param("kind") NetworkPostKind kind, @Param("excluded") Collection<Long> excluded, Pageable page);

    /** How many posts of the home feed (as {@link #feed}) by others than the reader came after the given time. */
    @Query("select count(distinct p) from NetworkPost p left join p.tags t where (p.authorId in :authors or t in :tags) " +
        "and p.authorId not in :excluded and p.authorId <> :reader and p.createdAt > :since")
    long feedNewer(@Param("authors") Collection<Long> authors, @Param("tags") Collection<String> tags,
                   @Param("excluded") Collection<Long> excluded, @Param("reader") Long reader, @Param("since") LocalDateTime since);

    /** The posts since then, with their tags. */
    @Query("select distinct p from NetworkPost p left join fetch p.tags where p.createdAt >= :since")
    List<NetworkPost> recentWithTags(@Param("since") LocalDateTime since);

    /** The tags in use matching a LIKE pattern (an underscore escaped by a backslash), the most used first. */
    @Query("select t from NetworkPost p join p.tags t where t like :pattern escape '\\' group by t order by count(p) desc, t asc")
    List<String> tagsStartingWith(@Param("pattern") String pattern, Pageable page);

    /** The posts naming one of these tags since then, with their tags. */
    @Query("select distinct p from NetworkPost p join fetch p.tags t where p.id in " +
        "(select q.id from NetworkPost q join q.tags u where u in :tags and q.createdAt >= :since)")
    List<NetworkPost> taggedSince(@Param("tags") Collection<String> tags, @Param("since") LocalDateTime since);

    List<NetworkPost> findByAuthorIdAndCreatedAtAfter(Long authorId, LocalDateTime since);

    /** The latest posts, the newest first. */
    List<NetworkPost> findAllByOrderByCreatedAtDescIdDesc(Pageable page);

    /** A tag's posts but those of the excluded authors, the most liked (whatever the reaction) first, then the newest. */
    @Query(value = "select p from NetworkPost p join p.tags t where t = :tag and p.authorId not in :excluded " +
        "order by (select count(l) from NetworkLike l where l.postId = p.id) desc, p.createdAt desc, p.id desc",
        countQuery = "select count(p) from NetworkPost p join p.tags t where t = :tag and p.authorId not in :excluded")
    Page<NetworkPost> tagByLikes(@Param("tag") String tag, @Param("excluded") Collection<Long> excluded, Pageable page);
}
