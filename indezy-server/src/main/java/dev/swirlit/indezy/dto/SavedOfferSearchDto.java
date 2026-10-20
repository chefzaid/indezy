package dev.swirlit.indezy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A saved search with how many offers Indezy found for it since it was last opened. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavedOfferSearchDto {

    private Long id;
    private String name;
    private OfferSearchRequest filters;
    private Instant lastOpenedAt;
    private long newOffers;
}
