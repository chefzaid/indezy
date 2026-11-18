package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.Testimonial;
import dev.swirlit.indezy.model.enums.TestimonialStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TestimonialRepository extends JpaRepository<Testimonial, Long> {

    List<Testimonial> findByFreelanceIdOrderByIdDesc(Long freelanceId);

    List<Testimonial> findByFreelanceIdAndStatusOrderByReceivedAtDesc(Long freelanceId, TestimonialStatus status);

    Optional<Testimonial> findByIdAndFreelanceId(Long id, Long freelanceId);

    Optional<Testimonial> findByTokenHash(String tokenHash);

    long countByFreelanceIdAndStatus(Long freelanceId, TestimonialStatus status);

    /** How many testimonials the workspace asked by email since the given time. */
    long countByFreelanceIdAndRecipientEmailIsNotNullAndCreatedAtAfter(Long freelanceId, LocalDateTime since);
}
