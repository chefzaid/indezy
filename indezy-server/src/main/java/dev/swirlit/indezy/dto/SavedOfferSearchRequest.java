package dev.swirlit.indezy.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The feed's current filters, to save under a name. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavedOfferSearchRequest {

    @NotBlank
    @Size(max = 80)
    private String name;

    @NotNull
    @Valid
    private OfferSearchRequest filters;
}
