package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    List<Webhook> findByFreelanceIdOrderByIdAsc(Long freelanceId);

    List<Webhook> findByFreelanceIdAndActiveTrue(Long freelanceId);

    Optional<Webhook> findByIdAndFreelanceId(Long id, Long freelanceId);

    long countByFreelanceId(Long freelanceId);
}
