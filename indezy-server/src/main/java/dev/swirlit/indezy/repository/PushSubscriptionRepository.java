package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByUserId(Long userId);

    /** The accounts with at least one browser to tell. */
    @Query("select distinct s.userId from PushSubscription s")
    List<Long> findUserIds();

    Optional<PushSubscription> findByEndpoint(String endpoint);

    @Transactional
    long deleteByUserIdAndEndpoint(Long userId, String endpoint);

    @Transactional
    long deleteByEndpoint(String endpoint);
}
