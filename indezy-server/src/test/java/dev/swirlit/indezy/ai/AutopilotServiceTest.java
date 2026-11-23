package dev.swirlit.indezy.ai;

import dev.swirlit.indezy.dto.OfferDto;
import dev.swirlit.indezy.dto.OfferPageDto;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.OfferVerdict;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.AutopilotDraftRepository;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.dto.DashboardStatsDto;
import dev.swirlit.indezy.model.AutopilotDraft;
import dev.swirlit.indezy.service.ContactInteractionService;
import org.mockito.ArgumentCaptor;
import dev.swirlit.indezy.repository.JobOfferRepository;
import dev.swirlit.indezy.repository.OfferVerdictRepository;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.service.OfferSearchService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutopilotServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final OfferSearchService search = mock(OfferSearchService.class);
    private final OfferTriageAiService triage = mock(OfferTriageAiService.class);
    private final OfferVerdictRepository verdicts = mock(OfferVerdictRepository.class);
    private final AiService ai = mock(AiService.class);
    private final ContactInteractionService interactions = mock(ContactInteractionService.class);
    private final MessageAiService messages = mock(MessageAiService.class);
    private final AutopilotDraftRepository drafts = mock(AutopilotDraftRepository.class);
    private final AutopilotService autopilot = new AutopilotService(users, freelances, search, triage, verdicts, mock(JobOfferRepository.class), ai,
        interactions, messages, drafts, mock(ContactRepository.class), Clock.fixed(Instant.parse("2026-11-24T03:10:00Z"), ZoneOffset.UTC));

    private User member(boolean on) {
        User user = new User();
        user.setId(3L);
        user.setEmail("lea@example.com");
        user.setAutopilot(on);
        user.setLanguagePreference("fr");
        Freelance freelance = new Freelance();
        freelance.setId(9L);
        when(freelances.findByEmail("lea@example.com")).thenReturn(Optional.of(freelance));
        when(ai.status(3L)).thenReturn(new AiDtos.Status(true, null, false, null, Map.of(), 0, 10, Map.of()));
        return user;
    }

    private void found(long count) {
        List<OfferDto> offers = LongStream.rangeClosed(1, count).mapToObj(id -> {
            OfferDto offer = mock(OfferDto.class);
            when(offer.id()).thenReturn(id);
            return offer;
        }).toList();
        when(search.foundBetween(eq(9L), any(), any(), any(), anyInt())).thenReturn(new OfferPageDto(offers, 0, offers.size(), offers.size(), 1, false, true));
    }

    @Test
    void triagesTheNewOffersThatFitWithoutAVerdictTenANightAtMost() {
        User user = member(true);
        found(14);
        when(verdicts.findByFreelanceIdAndOfferId(9L, 2L)).thenReturn(Optional.of(new OfferVerdict()));

        assertThat(autopilot.run(user)).isEqualTo(AutopilotService.OFFERS);

        verify(triage, never()).triage(3L, 9L, 2L, DocumentLanguage.FR);
        verify(triage).triage(3L, 9L, 11L, DocumentLanguage.FR);
        verify(triage, never()).triage(3L, 9L, 12L, DocumentLanguage.FR);
    }

    @Test
    void stopsWhenTheAllowanceIsUsedUpAndStaysOffUntilTurnedOn() {
        User user = member(true);
        found(5);
        when(triage.triage(3L, 9L, 3L, DocumentLanguage.FR)).thenThrow(new InvalidStateException("Your AI allowance for this month is used up"));

        assertThat(autopilot.run(user)).isEqualTo(2);
        verify(triage, times(3)).triage(eq(3L), eq(9L), anyLong(), eq(DocumentLanguage.FR));

        assertThat(autopilot.run(member(false))).isZero();
        User again = member(true);
        when(ai.status(3L)).thenReturn(new AiDtos.Status(false, "NO_KEY", false, null, Map.of(), 0, 10, Map.of()));
        assertThat(autopilot.run(again)).isZero();
    }

    private static DashboardStatsDto.FollowUpDue due(long contactId, Long projectId) {
        return DashboardStatsDto.FollowUpDue.builder().contactId(contactId).name("Contact " + contactId).projectId(projectId).daysWaiting(9).build();
    }

    @Test
    void draftsAFollowUpForTheContactsWaitingAboutAnOpportunityNotDraftedThisWeek() {
        User user = member(true);
        when(interactions.followUpsDue(eq(9L), anyInt(), any())).thenReturn(List.of(due(1, 41L), due(2, null), due(3, 43L), due(4, 44L)));
        when(drafts.existsByFreelanceIdAndContactIdAndCreatedAtAfter(eq(9L), eq(3L), any())).thenReturn(true);
        when(messages.draft(eq(3L), anyLong(), eq(MessageAiService.Kind.FOLLOW_UP), eq(DocumentLanguage.FR), any()))
            .thenReturn(new MessageAiService.Draft("Point sur la mission", "Bonjour,\n\nOù en est le processus ?"));

        assertThat(autopilot.draft(user)).isEqualTo(2);

        ArgumentCaptor<AutopilotDraft> saved = ArgumentCaptor.forClass(AutopilotDraft.class);
        verify(drafts, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(AutopilotDraft::getContactId).containsExactly(1L, 4L);
        assertThat(saved.getAllValues().getFirst().getSubject()).isEqualTo("Point sur la mission");
        assertThat(autopilot.draft(member(false))).isZero();
    }
}
