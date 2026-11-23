package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.HelpFeedbackDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HelpFeedbackDayRepository extends JpaRepository<HelpFeedbackDay, Long> {

    Optional<HelpFeedbackDay> findBySlugAndDay(String slug, LocalDate day);

    long countByDay(LocalDate day);

    /** Each article's answers since {@code since}: its slug, then the helpful and unhelpful counts, the most answered first. */
    @Query("select f.slug, sum(f.helpful), sum(f.unhelpful) from HelpFeedbackDay f where f.day >= :since group by f.slug " +
        "order by sum(f.helpful) + sum(f.unhelpful) desc, f.slug")
    List<Object[]> totalsSince(@Param("since") LocalDate since);
}
