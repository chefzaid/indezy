package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.OfferRateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OfferRateHistoryRepository extends JpaRepository<OfferRateHistory, Long> {

    /** A skill's nights in a region (or in all of France) from {@code since}, oldest first. */
    List<OfferRateHistory> findBySkillKeyAndRegionCodeAndDayGreaterThanEqualOrderByDay(String skillKey, String regionCode, LocalDate since);

    @Modifying
    @Query("delete from OfferRateHistory h where h.day = :day")
    int forgetDay(@Param("day") LocalDate day);

    @Modifying
    @Query("delete from OfferRateHistory h where h.day < :day")
    int forgetBefore(@Param("day") LocalDate day);
}
