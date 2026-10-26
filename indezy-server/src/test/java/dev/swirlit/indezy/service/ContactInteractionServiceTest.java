package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ContactInteractionDto;
import dev.swirlit.indezy.dto.ContactInteractionsDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.enums.InteractionChannel;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static dev.swirlit.indezy.model.enums.InteractionChannel.EMAIL;
import static dev.swirlit.indezy.model.enums.InteractionChannel.LINKEDIN;
import static dev.swirlit.indezy.model.enums.InteractionChannel.PHONE;
import static dev.swirlit.indezy.model.enums.InteractionDirection.INCOMING;
import static dev.swirlit.indezy.model.enums.InteractionDirection.OUTGOING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ContactInteractionServiceTest {

    @Test
    void anEmptyHistorySaysNothing() {
        ContactInteractionsDto summary = ContactInteractionService.summarize(List.of());

        assertThat(summary.interactions()).isEmpty();
        assertThat(summary.lastContactedOn()).isNull();
        assertThat(summary.asked()).isZero();
        assertThat(summary.responseRate()).isNull();
        assertThat(summary.preferredChannel()).isNull();
    }

    @Test
    void readsTheResponseRateFromTheAnswersKnown() {
        ContactInteractionsDto summary = ContactInteractionService.summarize(List.of(
            interaction(4, EMAIL, OUTGOING, null),
            interaction(3, EMAIL, OUTGOING, false),
            interaction(2, PHONE, OUTGOING, true),
            interaction(1, EMAIL, OUTGOING, false)));

        assertThat(summary.lastContactedOn()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(summary.asked()).isEqualTo(3);
        assertThat(summary.answered()).isEqualTo(1);
        assertThat(summary.responseRate()).isEqualTo(33);
        // Unanswered emails say nothing about how the contact likes to talk.
        assertThat(summary.preferredChannel()).isEqualTo(PHONE);
    }

    @Test
    void prefersTheChannelTheContactEngagesOnMostAndTheLatestOnATie() {
        assertThat(ContactInteractionService.summarize(List.of(
            interaction(4, LINKEDIN, INCOMING, null),
            interaction(3, EMAIL, OUTGOING, true),
            interaction(2, EMAIL, INCOMING, null),
            interaction(1, LINKEDIN, OUTGOING, true))).preferredChannel()).isEqualTo(LINKEDIN);
        assertThat(ContactInteractionService.summarize(List.of(
            interaction(4, PHONE, INCOMING, null),
            interaction(3, EMAIL, INCOMING, null),
            interaction(2, EMAIL, OUTGOING, true),
            interaction(1, PHONE, INCOMING, null),
            interaction(0, EMAIL, INCOMING, null))).preferredChannel()).isEqualTo(EMAIL);
    }

    @Test
    void asksTheDatabaseNothingForNoContacts() {
        ContactInteractionRepository repository = mock(ContactInteractionRepository.class);
        ContactInteractionService service = new ContactInteractionService(repository, mock(ContactRepository.class),
            Clock.systemUTC());

        assertThat(service.lastContacted(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void refusesAnExchangeBeyondTheLimit() {
        ContactInteractionRepository repository = mock(ContactInteractionRepository.class);
        when(repository.countByContactId(1L)).thenReturn((long) ContactInteractionService.MAX_INTERACTIONS);
        ContactInteractionService service = new ContactInteractionService(repository, mock(ContactRepository.class),
            Clock.systemUTC());

        assertThatThrownBy(() -> service.create(1L, new ContactInteractionDto(null, null, LocalDate.of(2026, 9, 1), EMAIL,
            OUTGOING, null, null)))
            .isInstanceOf(InvalidStateException.class)
            .hasMessage("A contact keeps at most 500 interactions");
        verify(repository, never()).save(any());
    }

    private static ContactInteraction interaction(int day, InteractionChannel channel, InteractionDirection direction,
                                                  Boolean replied) {
        Contact contact = new Contact();
        contact.setId(1L);
        ContactInteraction interaction = new ContactInteraction();
        interaction.setContact(contact);
        interaction.setOccurredOn(LocalDate.of(2026, 9, 1).plusDays(day - 1L));
        interaction.setChannel(channel);
        interaction.setDirection(direction);
        interaction.setReplied(replied);
        return interaction;
    }
}
