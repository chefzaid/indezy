package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.enums.InteractionChannel;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * An exchange with a contact. {@code replied} only applies to an {@code OUTGOING} one; {@code id} and
 * {@code contactId} are read-only.
 */
public record ContactInteractionDto(
    Long id,
    Long contactId,
    @NotNull LocalDate occurredOn,
    @NotNull InteractionChannel channel,
    @NotNull InteractionDirection direction,
    Boolean replied,
    @Size(max = ContactInteraction.SUMMARY_MAX) String summary) {

    // Spring MVC logs bodies through toString at DEBUG: never the summary (see docs/security.md).
    @Override
    public String toString() {
        return "ContactInteractionDto[id=" + id + ", channel=" + channel + ", direction=" + direction + "]";
    }
}
