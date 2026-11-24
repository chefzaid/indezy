package dev.swirlit.indezy.service;

import dev.swirlit.indezy.ai.AiDtos;
import dev.swirlit.indezy.ai.AiService;
import dev.swirlit.indezy.ai.ReplySortingAiService;
import dev.swirlit.indezy.model.Client;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.ContactInteraction;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Project;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.enums.ReplyKind;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.ProjectRepository;
import dev.swirlit.indezy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnswerFollowThroughServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    private final ReplySortingAiService sorting = mock(ReplySortingAiService.class);
    private final AiService ai = mock(AiService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final AnswerFollowThroughService service = new AnswerFollowThroughService(sorting, ai, users,
        mock(FreelanceRepository.class), projects, mock(ProjectRepository.class));
    private Contact contact;
    private ContactInteraction answer;

    @BeforeEach
    void setUp() {
        Freelance freelance = new Freelance();
        freelance.setId(8L);
        freelance.setEmail("alice@example.com");
        contact = new Contact();
        contact.setId(4L);
        contact.setFreelance(freelance);
        answer = new ContactInteraction();
        answer.setContact(contact);
        User user = new User();
        user.setId(3L);
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(ai.status(3L)).thenReturn(new AiDtos.Status(true, null, false, null, Map.of(), 0, 10, Map.of()));
    }

    private void sorted(ReplyKind kind, LocalDate writeBackOn) {
        doReturn(new ReplySortingAiService.Sort(kind, writeBackOn)).when(sorting).sort(any(), any(), any(), any());
        service.followThrough(answer, new ContactInteraction(), "Re: Disponible", "Bonjour", TODAY);
    }

    @Test
    void aNotNowIsWrittenBackOnTheDayItGaveElseThreeMonthsLater() {
        sorted(ReplyKind.NOT_NOW, LocalDate.of(2027, 2, 1));
        assertThat(answer.getWriteBackOn()).isEqualTo(LocalDate.of(2027, 2, 1));
        sorted(ReplyKind.NOT_NOW, null);
        assertThat(answer.getReplyKind()).isEqualTo(ReplyKind.NOT_NOW);
        assertThat(answer.getWriteBackOn()).isEqualTo(LocalDate.of(2027, 2, 24));
    }

    @Test
    void anInterestedAnswerOpensNoOpportunityWithoutACompanyOrWhenTheEmailWasAboutOne() {
        sorted(ReplyKind.INTERESTED, null);
        contact.setClient(new Client());
        answer.setProject(new Project());
        sorted(ReplyKind.INTERESTED, null);
        assertThat(answer.getReplyKind()).isEqualTo(ReplyKind.INTERESTED);
        verify(projects, never()).create(any());
    }

    @Test
    void anAnswerStaysUnsortedWithoutAiOrWhenSortingFails() {
        when(ai.status(3L)).thenReturn(new AiDtos.Status(false, null, false, null, Map.of(), 0, 0, Map.of()));
        service.followThrough(answer, new ContactInteraction(), "Re: x", "y", TODAY);
        verifyNoInteractions(sorting);

        when(ai.status(3L)).thenReturn(new AiDtos.Status(true, null, false, null, Map.of(), 0, 10, Map.of()));
        when(sorting.sort(any(), any(), any(), any())).thenThrow(new IllegalStateException("down"));
        service.followThrough(answer, new ContactInteraction(), "Re: x", "y", TODAY);
        assertThat(answer.getReplyKind()).isNull();

        sorted(ReplyKind.NO_NEED, null);
        assertThat(answer.getReplyKind()).isEqualTo(ReplyKind.NO_NEED);
        assertThat(answer.getWriteBackOn()).isNull();
        assertThat(contact.getDoNotContact()).isNotEqualTo(Boolean.TRUE);
    }

    @Test
    void takesTheRoleFromTheSubjectWithoutItsReplyPrefixes() {
        assertThat(AnswerFollowThroughService.role("RE: TR : Re:Mission Java")).isEqualTo("Mission Java");
        assertThat(AnswerFollowThroughService.role("Re: ")).isEqualTo("Opportunité");
        assertThat(AnswerFollowThroughService.role(null)).isEqualTo("Opportunité");
        assertThat(AnswerFollowThroughService.role("x".repeat(300))).hasSize(120);
    }
}
