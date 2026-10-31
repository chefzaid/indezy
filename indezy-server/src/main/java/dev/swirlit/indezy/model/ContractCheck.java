package dev.swirlit.indezy.model;

import dev.swirlit.indezy.model.enums.ContractPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One point of a mission's contract checklist: whether it was checked and what the contract says about it. */
@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ContractCheck {

    @Enumerated(EnumType.STRING)
    @Column(name = "check_point", nullable = false, length = 40)
    private ContractPoint point;

    @Column(name = "checked", nullable = false)
    private boolean checked;

    @Column(name = "note", length = 500)
    private String note;
}
