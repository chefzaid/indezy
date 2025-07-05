package dev.byteworks.indezy.repository;

import dev.byteworks.indezy.model.Freelance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FreelanceRepository extends JpaRepository<Freelance, Long> {

    Optional<Freelance> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT f FROM Freelance f LEFT JOIN FETCH f.projects WHERE f.id = :id")
    Optional<Freelance> findByIdWithProjects(@Param("id") Long id);

    @Query("SELECT f FROM Freelance f LEFT JOIN FETCH f.clients WHERE f.id = :id")
    Optional<Freelance> findByIdWithClients(@Param("id") Long id);

    @Query("SELECT f FROM Freelance f LEFT JOIN FETCH f.sources WHERE f.id = :id")
    Optional<Freelance> findByIdWithSources(@Param("id") Long id);
}
