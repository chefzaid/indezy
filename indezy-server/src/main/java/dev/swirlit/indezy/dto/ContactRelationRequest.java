package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.ContactRelation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Links a contact to {@code otherId}, who works with them, saying how (optional). */
public record ContactRelationRequest(@NotNull Long otherId, @Size(max = ContactRelation.NOTE_MAX) String note) {

    @Override
    public String toString() {
        return "ContactRelationRequest[otherId=" + otherId + "]";
    }
}
