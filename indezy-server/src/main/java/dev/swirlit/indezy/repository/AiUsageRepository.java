package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.ai.AiFeature;
import dev.swirlit.indezy.model.AiUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {

    Optional<AiUsage> findByUserIdAndMonthAndFeatureAndOwnKey(Long userId, String month, AiFeature feature, boolean ownKey);

    /** What one account spent on Indezy's key in the month, in millionths of a dollar. */
    @Query("SELECT COALESCE(SUM(u.costMicros), 0) FROM AiUsage u WHERE u.userId = :userId AND u.month = :month AND u.ownKey = false")
    long spentOnIndezyKey(@Param("userId") Long userId, @Param("month") String month);

    /** What every account spent on Indezy's key in the month. */
    @Query("SELECT COALESCE(SUM(u.costMicros), 0) FROM AiUsage u WHERE u.month = :month AND u.ownKey = false")
    long spentOnIndezyKey(@Param("month") String month);
}
