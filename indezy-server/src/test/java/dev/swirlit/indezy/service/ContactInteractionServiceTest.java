package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ContactInteractionDto;
import dev.swirlit.indezy.dto.ContactInteractionsDto;
import dev.swirlit.indezy.dto.DashboardStatsDto;
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
import static org.assertj.core.api.Assertions.tuple;
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

    @Test
    void listsTheContactsWhoseLatestMessageWaitsPastTheFollowUpPeriod() {
        ContactInteractionRepository repository = mock(ContactInteractionRepository.class);
        ContactInteraction waiting = interaction(1, EMAIL, OUTGOING, null);
        ContactInteraction older = interaction(-10, PHONE, OUTGOING, null);
        older.setContact(contact(2L, "Bob"));
        ContactInteraction answeredLater = interaction(3, EMAIL, INCOMING, null);
        answeredLater.setContact(contact(3L, "Zoé"));
        ContactInteraction before = interaction(1, EMAIL, OUTGOING, null);
        before.setContact(contact(3L, "Zoé"));
        ContactInteraction recent = interaction(9, LINKEDIN, OUTGOING, null);
        recent.setContact(contact(4L, "Dan"));
        when(repository.findByContactFreelanceIdOrderByContactIdAscOccurredOnDescIdDesc(7L))
            .thenReturn(List.of(waiting, older, answeredLater, before, recent));
        ContactInteractionService service = new ContactInteractionService(repository, mock(ContactRepository.class),
            Clock.systemUTC());

        List<DashboardStatsDto.FollowUpDue> due = service.followUpsDue(7L, 5, LocalDate.of(2026, 9, 12));

        assertThat(due).extracting(DashboardStatsDto.FollowUpDue::getName, DashboardStatsDto.FollowUpDue::getDaysWaiting)
            .containsExactly(tuple("Bob", 22L), tuple("Alice", 11L));
        assertThat(due.get(1).getChannel()).isEqualTo(EMAIL);
        assertThat(due.get(1).getSentOn()).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    private static Contact contact(Long id, String firstName) {
        Contact contact = new Contact();
        contact.setId(id);
        contact.setFirstName(firstName);
        return contact;
    }

    private static ContactInteraction interaction(int day, InteractionChannel channel, InteractionDirection direction,
                                                  Boolean replied) {
        ContactInteraction interaction = new ContactInteraction();
        interaction.setContact(contact(1L, "Alice"));
        interaction.setOccurredOn(LocalDate.of(2026, 9, 1).plusDays(day - 1L));
        interaction.setChannel(channel);
        interaction.setDirection(direction);
        interaction.setReplied(replied);
        return interaction;
    }
}
