package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.model.enums.SavedSearchEmails;

import java.time.Instant;

/**
 * A saved search's emails: how often, at which hour (Paris time) for a daily one, in which language, and when sending
 * stopped because no offer was opened from 60 days of them ({@code null} otherwise).
 */
public record SavedSearchEmailsDto(SavedSearchEmails frequency, Integer hour, DocumentLanguage language, Instant stoppedAt) {
}
