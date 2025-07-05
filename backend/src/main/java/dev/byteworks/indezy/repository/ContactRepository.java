package dev.byteworks.indezy.repository;

import dev.byteworks.indezy.model.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByFreelanceId(Long freelanceId);

    List<Contact> findByClientId(Long clientId);

    @Query("SELECT c FROM Contact c WHERE c.freelance.id = :freelanceId AND (c.firstName LIKE %:name% OR c.lastName LIKE %:name%)")
    List<Contact> findByFreelanceIdAndNameContaining(@Param("freelanceId") Long freelanceId, @Param("name") String name);

    @Query("SELECT c FROM Contact c WHERE c.freelance.id = :freelanceId AND c.email LIKE %:email%")
    List<Contact> findByFreelanceIdAndEmailContaining(@Param("freelanceId") Long freelanceId, @Param("email") String email);

    @Query("SELECT c FROM Contact c WHERE c.client.id = :clientId")
    List<Contact> findByClientIdOrderByFirstName(@Param("clientId") Long clientId);

    boolean existsByEmailAndFreelanceId(String email, Long freelanceId);
}
