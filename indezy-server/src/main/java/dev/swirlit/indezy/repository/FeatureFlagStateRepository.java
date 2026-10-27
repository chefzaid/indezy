package dev.swirlit.indezy.repository;

import dev.swirlit.indezy.model.FeatureFlagState;
import dev.swirlit.indezy.model.enums.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeatureFlagStateRepository extends JpaRepository<FeatureFlagState, Long> {

    Optional<FeatureFlagState> findByFlag(FeatureFlag flag);
}
