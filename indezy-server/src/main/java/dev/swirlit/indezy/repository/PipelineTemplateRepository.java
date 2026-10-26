package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.PipelineTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PipelineTemplateRepository extends JpaRepository<PipelineTemplate, Long> {

    List<PipelineTemplate> findByFreelanceIdOrderByNameAsc(Long freelanceId);

    long countByFreelanceId(Long freelanceId);

    boolean existsByFreelanceIdAndNameIgnoreCase(Long freelanceId, String name);

    @Query("SELECT t.freelance.id FROM PipelineTemplate t WHERE t.id = :id")
    Optional<Long> findOwnerFreelanceIdById(@Param("id") Long id);

    /** Keeps the templates of a deleted client or ESN, for anyone; templates already loaded are read again. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PipelineTemplate t SET t.client = null WHERE t.client.id = :clientId")
    int detachClient(@Param("clientId") Long clientId);
}
