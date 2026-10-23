package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.SavedSearchEmails;

/** What an unsubscribe link shows without an account: the search's name, whether it still emails, in which language. */
public record PublicSavedSearchEmailsDto(String searchName, SavedSearchEmails frequency, DocumentLanguage language) {
}
