package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.ContactLegalBasis;
import dev.swirlit.indezy.model.enums.InteractionChannel;
import dev.swirlit.indezy.model.enums.InteractionDirection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * What the workspace holds about one contact, handed to them on request (GDPR article 15): their details, why and
 * since when they are kept, the exchanges with them and the notes on whom they work with. Others' names are left out.
 */
public record ContactPersonalDataDto(String keptBy, LocalDate exportedOn, String firstName, String lastName, String email, String phone,
                                     String company, String notes, ContactLegalBasis legalBasis, LocalDateTime keptSince,
                                     LocalDateTime lastChanged, List<Exchange> exchanges, List<Link> worksWith) {

    public ContactPersonalDataDto {
        exchanges = exchanges == null ? List.of() : List.copyOf(exchanges);
        worksWith = worksWith == null ? List.of() : List.copyOf(worksWith);
    }

    /** One exchange with the contact. */
    public record Exchange(LocalDate on, InteractionChannel channel, InteractionDirection direction, Boolean answered, String summary) {
    }

    /** Someone the contact works with, by company only, and the note on how. */
    public record Link(String company, String note) {
    }

    @Override
    public String toString() {
        // Personal data, kept out of logs.
        return "ContactPersonalData[" + exportedOn + "]";
    }
}
