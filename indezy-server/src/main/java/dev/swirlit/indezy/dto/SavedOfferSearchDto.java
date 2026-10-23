package dev.swirlit.indezy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A saved search with how many offers Indezy found for it since it was last opened, and its emails. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavedOfferSearchDto {

    private Long id;
    private String name;
    private OfferSearchRequest filters;
    private Instant lastOpenedAt;
    private long newOffers;
    private SavedSearchEmailsDto emails;
    /** Whether this server can send email (a Resend key is set). */
    private boolean emailsAvailable;
}
