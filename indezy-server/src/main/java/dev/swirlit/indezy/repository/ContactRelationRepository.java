package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ContactRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContactRelationRepository extends JpaRepository<ContactRelation, Long> {

    @Query("SELECT r FROM ContactRelation r JOIN FETCH r.first f JOIN FETCH r.second s " +
        "LEFT JOIN FETCH f.client LEFT JOIN FETCH s.client WHERE f.id IN :contactIds OR s.id IN :contactIds")
    List<ContactRelation> findInvolving(@Param("contactIds") List<Long> contactIds);

    @Query("SELECT r FROM ContactRelation r WHERE r.first.freelance.id = :freelanceId ORDER BY r.first.id, r.second.id")
    List<ContactRelation> findByFreelanceId(@Param("freelanceId") Long freelanceId);

    Optional<ContactRelation> findByFirstIdAndSecondId(Long firstId, Long secondId);

    @Query("SELECT COUNT(r) FROM ContactRelation r WHERE r.first.id = :contactId OR r.second.id = :contactId")
    long countInvolving(@Param("contactId") Long contactId);
}
