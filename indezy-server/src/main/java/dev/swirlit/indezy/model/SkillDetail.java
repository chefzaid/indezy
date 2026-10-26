package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.SkillLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What a profile says about one of its skills: a level, years of experience, or both. */
@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class SkillDetail {

    @Enumerated(EnumType.STRING)
    @Column(name = "level")
    private SkillLevel level;

    @Column(name = "years")
    private Integer years;

    /** Whether it says anything. */
    public boolean isEmpty() {
        return level == null && years == null;
    }
}
