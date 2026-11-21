package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByUserId(Long userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    @Transactional
    long deleteByUserIdAndEndpoint(Long userId, String endpoint);

    @Transactional
    long deleteByEndpoint(String endpoint);
}
