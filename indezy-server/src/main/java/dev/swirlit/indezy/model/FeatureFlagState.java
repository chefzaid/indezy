package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.FeatureFlag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Whether a feature is on; a feature without a row is on. */
@Entity
@Table(name = "feature_flags")
@Getter
@Setter
public class FeatureFlagState extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "flag", nullable = false, unique = true, length = 40)
    private FeatureFlag flag;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;
}
