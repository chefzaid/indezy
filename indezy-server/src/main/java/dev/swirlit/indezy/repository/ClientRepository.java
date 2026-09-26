package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    List<Client> findByFreelanceId(Long freelanceId);

    /** Owner workspace of the client, used by access checks without loading the entity graph. */
    @Query("SELECT c.freelance.id FROM Client c WHERE c.id = :id")
    Optional<Long> findOwnerFreelanceIdById(@Param("id") Long id);
}
