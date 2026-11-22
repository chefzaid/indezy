package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.SupportRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, Long> {

    List<SupportRequest> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** The requests for the administrators: open ones first, then the newest. */
    @Query("select r from SupportRequest r order by case when r.status = dev.swirlit.indezy.model.SupportRequest.Status.OPEN then 0 else 1 end, " +
        "r.createdAt desc")
    List<SupportRequest> findForAdministrators(Pageable page);
}
