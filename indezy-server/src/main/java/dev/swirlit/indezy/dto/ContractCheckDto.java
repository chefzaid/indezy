package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ContractPoint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One point of a mission's contract checklist (see docs/features.md#contract-checklist). */
public record ContractCheckDto(@NotNull ContractPoint point, boolean checked, @Size(max = 500) String note) {
}
