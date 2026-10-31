package dev.swirlit.indezy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** A mission's whole contract checklist, each point at most once. */
public record ContractChecksRequest(@NotNull @Size(max = 7) List<@Valid @NotNull ContractCheckDto> checks) {

    public ContractChecksRequest {
        checks = checks == null ? null : List.copyOf(checks);
    }
}
