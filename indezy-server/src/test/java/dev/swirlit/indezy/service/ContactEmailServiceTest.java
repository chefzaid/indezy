package dev.swirlit.indezy.service;

import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.mail.ContactEmailComposer;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactEmailServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    @Mock
    private ResendMailer mailer;
    @Mock
    private ContactEmailComposer composer;
    @Mock
    private ContactRepository contacts;
    @Mock
    private ContactInteractionRepository interactions;
    @Mock
    private ProjectRepository projects;
    @Mock
    private FreelanceRepository freelances;

    @Test
    void sendsAtMostFiftyEmailsADayForAWorkspace() throws Exception {
        ContactEmailService service = new ContactEmailService(mailer, composer, contacts, interactions, projects, freelances,
            Clock.fixed(NOW, ZoneOffset.UTC));
        Contact contact = new Contact();
        contact.setEmail("lea@acme.example");
        when(mailer.configured()).thenReturn(true);
        when(contacts.findById(3L)).thenReturn(Optional.of(contact));
        when(freelances.findById(1L)).thenReturn(Optional.of(new Freelance()));
        when(interactions.countByContactFreelanceIdAndEmailSentAtAfter(eq(1L), any())).thenReturn(50L);

        assertThatThrownBy(() -> service.send(1L, 3L, new ContactEmailService.Message(null, "Relance", "Bonjour", false, false, null)))
            .isInstanceOf(InvalidStateException.class).hasMessageStartingWith("Up to 50 emails a day go from Indezy");
        verify(mailer, never()).send(any());
        verify(interactions, never()).save(any());
    }
}
