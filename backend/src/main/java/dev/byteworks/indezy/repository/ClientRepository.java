package dev.byteworks.indezy.repository;

import dev.byteworks.indezy.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findByFreelanceId(Long freelanceId);

    List<Client> findByFreelanceIdAndIsFinal(Long freelanceId, Boolean isFinal);

    @Query("SELECT c FROM Client c WHERE c.freelance.id = :freelanceId AND c.companyName LIKE %:companyName%")
    List<Client> findByFreelanceIdAndCompanyNameContaining(@Param("freelanceId") Long freelanceId, @Param("companyName") String companyName);

    @Query("SELECT c FROM Client c WHERE c.freelance.id = :freelanceId AND c.city = :city")
    List<Client> findByFreelanceIdAndCity(@Param("freelanceId") Long freelanceId, @Param("city") String city);

    @Query("SELECT c FROM Client c LEFT JOIN FETCH c.projects WHERE c.id = :id")
    Optional<Client> findByIdWithProjects(@Param("id") Long id);

    @Query("SELECT c FROM Client c LEFT JOIN FETCH c.contacts WHERE c.id = :id")
    Optional<Client> findByIdWithContacts(@Param("id") Long id);

    @Query("SELECT DISTINCT c.city FROM Client c WHERE c.freelance.id = :freelanceId ORDER BY c.city")
    List<String> findDistinctCitiesByFreelanceId(@Param("freelanceId") Long freelanceId);

    @Query("SELECT DISTINCT c.domain FROM Client c WHERE c.freelance.id = :freelanceId AND c.domain IS NOT NULL ORDER BY c.domain")
    List<String> findDistinctDomainsByFreelanceId(@Param("freelanceId") Long freelanceId);
}
