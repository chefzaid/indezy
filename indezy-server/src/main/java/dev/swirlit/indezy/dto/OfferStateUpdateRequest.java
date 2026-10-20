package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.OfferNotes;
import jakarta.validation.constraints.Size;

/**
 * Changes to what the workspace did with an offer; a {@code null} field keeps its value, and a blank {@code note}
 * clears the note.
 */
public record OfferStateUpdateRequest(Boolean saved, Boolean hidden, Boolean seen, @Size(max = OfferNotes.MAX_LENGTH) String note) {

    public OfferStateUpdateRequest(Boolean saved, Boolean hidden, Boolean seen) {
        this(saved, hidden, seen, null);
    }
}
