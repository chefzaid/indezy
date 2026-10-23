package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.SavedSearchEmails;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * How often a saved search should email its new offers; {@code hour} (Paris time) is a daily email's, 8 when absent,
 * and {@code language} the one the emails are written in, the app's current one.
 */
public record SavedSearchEmailsRequest(
    @NotNull(message = "Frequency is required") SavedSearchEmails frequency,
    @Min(value = 0, message = "Hour must be between 0 and 23") @Max(value = 23, message = "Hour must be between 0 and 23") Integer hour,
    DocumentLanguage language) {
}
