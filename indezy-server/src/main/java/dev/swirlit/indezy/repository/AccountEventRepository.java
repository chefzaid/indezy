package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.AccountEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AccountEventRepository extends JpaRepository<AccountEvent, Long> {

    Page<AccountEvent> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM AccountEvent e WHERE e.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
