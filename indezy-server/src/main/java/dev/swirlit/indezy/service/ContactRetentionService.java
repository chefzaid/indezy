package dev.swirlit.indezy.service;

import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Anonymizes the contacts dormant beyond their account's retention period (see docs/features.md#contact-privacy), for
 * the accounts that set one: no exchange, no change and no opportunity of their company changed for that many years.
 * Their name, email, phone, notes, exchange summaries and link notes are cleared; the contact stays, named
 * {@value #PLACEHOLDER}, so the counts and the org chart keep their shape.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactRetentionService {

    /** The name an anonymized contact keeps; the app shows it translated. */
    public static final String PLACEHOLDER = "Anonymized";

    private final FreelanceRepository freelances;
    private final ContactRepository contacts;
    private final AccountEventService accountEvents;

    /** Anonymizes every opted-in workspace's dormant contacts as of {@code now}; how many it anonymized. */
    @Transactional
    public int anonymizeDormant(LocalDateTime now) {
        int anonymized = 0;
        for (Object[] workspace : freelances.findContactRetentionWorkspaces()) {
            Long freelanceId = (Long) workspace[0];
            LocalDateTime before = now.minusYears((Integer) workspace[2]);
            List<Long> dormant = contacts.findDormant(freelanceId, before, before.toLocalDate());
            if (dormant.isEmpty()) {
                continue;
            }
            contacts.clearExchangeSummaries(dormant);
            contacts.clearLinkNotes(dormant);
            contacts.anonymize(dormant, PLACEHOLDER, now);
            accountEvents.record((Long) workspace[1], AccountEventType.CONTACTS_ANONYMIZED, String.valueOf(dormant.size()));
            anonymized += dormant.size();
        }
        if (anonymized > 0) {
            log.info("Anonymized {} dormant contacts", anonymized);
        }
        return anonymized;
    }
}
