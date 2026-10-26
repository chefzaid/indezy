package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.ContactInteraction;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ContactInteractionRepository extends JpaRepository<ContactInteraction, Long> {

    /** A contact and the day of its latest exchange. */
    interface LastInteraction {
        Long getContactId();

        LocalDate getLastOn();
    }

    List<ContactInteraction> findByContactIdOrderByOccurredOnDescIdDesc(Long contactId);

    List<ContactInteraction> findByContactFreelanceIdOrderByContactIdAscOccurredOnDescIdDesc(Long freelanceId);

    Optional<ContactInteraction> findByIdAndContactId(Long id, Long contactId);

    @EntityGraph(attributePaths = "contact")
    List<ContactInteraction> findByProjectIdOrderByOccurredOnDescIdDesc(Long projectId);

    /** The exchanges about the opportunity are about none: run before deleting it. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ContactInteraction i SET i.project = null WHERE i.project.id = :projectId")
    int clearProject(@Param("projectId") Long projectId);

    long countByContactId(Long contactId);

    @Query("SELECT i.contact.id AS contactId, MAX(i.occurredOn) AS lastOn FROM ContactInteraction i " +
        "WHERE i.contact.id IN :contactIds GROUP BY i.contact.id")
    List<LastInteraction> findLastByContactIds(@Param("contactIds") Collection<Long> contactIds);

    @Query("SELECT i.contact.id AS contactId, MAX(i.occurredOn) AS lastOn FROM ContactInteraction i " +
        "WHERE i.contact.freelance.id = :freelanceId GROUP BY i.contact.id")
    List<LastInteraction> findLastByFreelanceId(@Param("freelanceId") Long freelanceId);
}
