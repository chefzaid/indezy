package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.BookingPage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookingPageRepository extends JpaRepository<BookingPage, Long> {

    Optional<BookingPage> findByFreelanceId(Long freelanceId);

    Optional<BookingPage> findBySlug(String slug);

    /** The page, locked until the transaction ends, so two recruiters cannot book its same slot at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM BookingPage p WHERE p.slug = :slug")
    Optional<BookingPage> lockBySlug(@Param("slug") String slug);
}
