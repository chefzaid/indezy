package dev.swirlit.indezy.repository;

import java.util.Optional;
import dev.swirlit.indezy.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByFreelanceId(Long freelanceId);

    List<Contact> findByClientId(Long clientId);

    boolean existsByEmailAndFreelanceId(String email, Long freelanceId);

    /** Owner workspace of the contact, used by access checks without loading the entity graph. */
    @Query("SELECT c.freelance.id FROM Contact c WHERE c.id = :id")
    Optional<Long> findOwnerFreelanceIdById(@Param("id") Long id);

    /** The people {@code managerIds} managed now report to nobody: run before deleting them. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Contact c SET c.manager = null WHERE c.manager.id IN :managerIds")
    int clearManager(@Param("managerIds") Collection<Long> managerIds);

    /**
     * The workspace's contacts dormant since {@code before}: not anonymized yet, unchanged since, without an exchange
     * since, and whose company has no opportunity changed since.
     */
    @Query("SELECT c.id FROM Contact c WHERE c.freelance.id = :freelanceId AND c.anonymizedAt IS NULL " +
        "AND COALESCE(c.updatedAt, c.createdAt) < :before " +
        "AND NOT EXISTS (SELECT i.id FROM ContactInteraction i WHERE i.contact = c AND i.occurredOn >= :beforeDay) " +
        "AND NOT EXISTS (SELECT p.id FROM Project p WHERE (p.client = c.client OR p.middleman = c.client) " +
        "AND COALESCE(p.updatedAt, p.createdAt) >= :before)")
    List<Long> findDormant(@Param("freelanceId") Long freelanceId, @Param("before") LocalDateTime before,
                           @Param("beforeDay") LocalDate beforeDay);

    /** Clears what identifies the contacts, keeping them (and their exchanges' dates and channels) for the counts. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Contact c SET c.firstName = :placeholder, c.lastName = NULL, c.email = NULL, c.phone = NULL, c.notes = NULL, " +
        "c.anonymizedAt = :now WHERE c.id IN :ids")
    int anonymize(@Param("ids") Collection<Long> ids, @Param("placeholder") String placeholder, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ContactInteraction i SET i.summary = NULL WHERE i.contact.id IN :ids")
    int clearExchangeSummaries(@Param("ids") Collection<Long> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ContactRelation r SET r.note = NULL WHERE r.first.id IN :ids OR r.second.id IN :ids")
    int clearLinkNotes(@Param("ids") Collection<Long> ids);
}
