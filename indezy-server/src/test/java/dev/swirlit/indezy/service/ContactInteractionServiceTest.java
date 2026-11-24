package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.ContactInteractionDto;
import dev.swirlit.indezy.dto.ContactInteractionsDto;
import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.enums.InteractionChannel;
import dev.swirlit.indezy.model.enums.InteractionDirection;
import dev.swirlit.indezy.repository.ContactInteractionRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
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
            mock(ProjectRepository.class), Clock.systemUTC());

        assertThat(service.lastContacted(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void refusesAnExchangeBeyondTheLimit() {
        ContactInteractionRepository repository = mock(ContactInteractionRepository.class);
        when(repository.countByContactId(1L)).thenReturn((long) ContactInteractionService.MAX_INTERACTIONS);
        ContactInteractionService service = new ContactInteractionService(repository, mock(ContactRepository.class),
            mock(ProjectRepository.class), Clock.systemUTC());

        assertThatThrownBy(() -> service.create(1L, new ContactInteractionDto(null, null, null, LocalDate.of(2026, 9, 1), EMAIL,
            OUTGOING, null, null, null, null, null, null)))
            .isInstanceOf(InvalidStateException.class)
            .hasMessage("A contact keeps at most 500 interactions");
        verify(repository, never()).save(any());
    }

    @Test
    void listsTheContactsWhoseLatestMessageWaitsPastTheFollowUpPeriod() {
        ContactInteractionRepository repository = mock(ContactInteractionRepository.class);
        ContactInteraction waiting = interaction(1, EMAIL, OUTGOING, null);
        dev.swirlit.indezy.model.Project about = new dev.swirlit.indezy.model.Project();
        about.setId(42L);
        waiting.setProject(about);
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
            mock(ProjectRepository.class), Clock.systemUTC());

        List<DashboardStatsDto.FollowUpDue> due = service.followUpsDue(7L, 5, LocalDate.of(2026, 9, 12));

        assertThat(due).extracting(DashboardStatsDto.FollowUpDue::getName, DashboardStatsDto.FollowUpDue::getDaysWaiting)
            .containsExactly(tuple("Bob", 22L), tuple("Alice", 11L));
        assertThat(due.get(1).getChannel()).isEqualTo(EMAIL);
        assertThat(due.get(1).getSentOn()).isEqualTo(LocalDate.of(2026, 9, 1));
        // The follow-up is drafted from the opportunity the message was about.
        assertThat(due.get(1).getProjectId()).isEqualTo(42L);
        assertThat(due.get(0).getProjectId()).isNull();
    }

    @Test
    void timesAFollowUpFromTheCompanysUsualAnswerTime() {
        Client esn = new Client();
        esn.setId(50L);
        Contact lea = contact(6L, "Léa");
        lea.setClient(esn);
        Contact paul = contact(7L, "Paul");
        paul.setClient(esn);
        // Léa answered after 3, 2 and 5 days: the median is 3.
        List<ContactInteraction> leas = new java.util.ArrayList<>();
        int[][] exchanges = {{25, 1}, {20, 0}, {12, 1}, {10, 0}, {4, 1}, {1, 0}};
        for (int[] exchange : exchanges) {
            ContactInteraction interaction = interaction(exchange[0], EMAIL, exchange[1] == 1 ? INCOMING : OUTGOING, null);
            interaction.setContact(lea);
            leas.add(interaction);
        }
        ContactInteraction waiting = interaction(28, EMAIL, OUTGOING, null);
        waiting.setContact(paul);
        List<ContactInteraction> all = new java.util.ArrayList<>(leas);
        all.add(waiting);
        assertThat(ContactInteractionService.usualAnswerDays(all)).containsExactly(java.util.Map.entry(50L, 3));

        ContactInteractionRepository repository = mock(ContactInteractionRepository.class);
        when(repository.findByContactFreelanceIdOrderByContactIdAscOccurredOnDescIdDesc(7L)).thenReturn(all);
        ContactInteractionService service = new ContactInteractionService(repository, mock(ContactRepository.class),
            mock(ProjectRepository.class), Clock.systemUTC());
        // Paul waits 5 days: past Conseil's usual 3 days and its 2 days' grace, though the workspace's delay is 10.
        List<DashboardStatsDto.FollowUpDue> due = service.followUpsDue(7L, 10, LocalDate.of(2026, 9, 1).plusDays(32));
        assertThat(due).extracting(DashboardStatsDto.FollowUpDue::getName, DashboardStatsDto.FollowUpDue::getUsualAnswerDays)
            .containsExactly(tuple("Paul", 3));
        assertThat(service.followUpsDue(7L, 10, LocalDate.of(2026, 9, 1).plusDays(31))).isEmpty();
        // One answer is not enough to say.
        assertThat(ContactInteractionService.usualAnswerDays(leas.subList(4, 6))).isEmpty();
    }

    @Test
    void flagsTheCompaniesThatNeverAnswered() {
        Client silent = new Client();
        silent.setId(60L);
        silent.setCompanyName("Conseil Muet");
        Client talking = new Client();
        talking.setId(61L);
        talking.setCompanyName("Conseil Bavard");
        Contact max = contact(8L, "Max");
        max.setClient(silent);
        Contact ana = contact(9L, "Ana");
        ana.setClient(talking);
        List<ContactInteraction> all = new java.util.ArrayList<>();
        for (int day = 1; day <= 3; day++) {
            ContactInteraction toMax = interaction(day, EMAIL, OUTGOING, null);
            toMax.setContact(max);
            all.add(toMax);
            ContactInteraction toAna = interaction(day, EMAIL, OUTGOING, day == 2 ? true : null);
            toAna.setContact(ana);
            all.add(toAna);
        }
        assertThat(ContactInteractionService.unansweredCompanies(all)).containsExactly(java.util.Map.entry("Conseil Muet", 3));
        // Two messages are not enough to say.
        assertThat(ContactInteractionService.unansweredCompanies(all.subList(0, 4))).isEmpty();
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
