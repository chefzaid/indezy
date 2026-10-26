package dev.swirlit.indezy.repository;

import java.util.Optional;
import dev.swirlit.indezy.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
