package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.TestimonialDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Testimonial;
import dev.swirlit.indezy.model.enums.TestimonialStatus;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.TestimonialRepository;
import dev.swirlit.indezy.util.Tokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestimonialServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private final TestimonialRepository repository = mock(TestimonialRepository.class);
    private final ContactRepository contacts = mock(ContactRepository.class);
    private final FreelanceRepository freelances = mock(FreelanceRepository.class);
    private final TestimonialService service = new TestimonialService(repository, contacts, freelances, Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        when(repository.save(any(Testimonial.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Freelance alice = new Freelance();
        alice.setFirstName("Alice");
        alice.setLastName(" ");
        when(freelances.findById(1L)).thenReturn(Optional.of(alice));
    }

    private Testimonial stored(String token, TestimonialStatus status, Instant expiresAt) {
        Testimonial testimonial = new Testimonial();
        testimonial.setFreelanceId(1L);
        testimonial.setRecipientName("Léa");
        testimonial.setStatus(status);
        testimonial.setExpiresAt(expiresAt);
        when(repository.findByTokenHash(Tokens.sha256Hex(token))).thenReturn(Optional.of(testimonial));
        return testimonial;
    }

    @Test
    void asksThirtyDaysAndNoMoreThanFiftyAtOnce() {
        TestimonialDtos.Link link = service.request(1L, new TestimonialDtos.Request(null, " Paul "));
        assertThat(link.token()).isNotBlank();
        assertThat(link.testimonial().recipientName()).isEqualTo("Paul");
        assertThat(link.testimonial().expiresAt()).isEqualTo(NOW.plus(TestimonialService.LINK_VALIDITY));

        when(repository.countByFreelanceIdAndStatus(1L, TestimonialStatus.REQUESTED)).thenReturn((long) TestimonialService.MAX_OPEN_REQUESTS);
        assertThatThrownBy(() -> service.request(1L, new TestimonialDtos.Request(null, "Paul"))).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void refusesAnUnknownContactAndAStatusOtherThanShownOrHidden() {
        when(contacts.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.request(1L, new TestimonialDtos.Request(9L, null))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.request(1L, new TestimonialDtos.Request(null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setStatus(1L, 3L, TestimonialStatus.RECEIVED)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void opensAnExpiredLinkOnlyOnceAnsweredAndRefusesAnOverlongOrBlankToken() {
        stored("expired", TestimonialStatus.REQUESTED, NOW.minusSeconds(1));
        assertThatThrownBy(() -> service.open("expired")).isInstanceOf(ResourceNotFoundException.class);
        stored("answered", TestimonialStatus.APPROVED, NOW.minusSeconds(1));
        assertThat(service.open("answered")).isEqualTo(new TestimonialDtos.PublicRequest("Alice", "Léa", true));
        assertThatThrownBy(() -> service.open(" ")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.open(null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.open("x".repeat(129))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void keepsTheAnswerTrimmedAndNamesNoFreelancerWithoutAWorkspace() {
        Testimonial testimonial = stored("open", TestimonialStatus.REQUESTED, NOW.plusSeconds(60));
        service.answer("open", new TestimonialDtos.Answer(" Léa Martin ", " ", " Un consultant rigoureux et très fiable. ", true));
        assertThat(testimonial.getAuthorName()).isEqualTo("Léa Martin");
        assertThat(testimonial.getAuthorTitle()).isNull();
        assertThat(testimonial.getText()).isEqualTo("Un consultant rigoureux et très fiable.");
        assertThat(testimonial.getReceivedAt()).isEqualTo(NOW);

        testimonial.setFreelanceId(2L);
        when(freelances.findById(2L)).thenReturn(Optional.empty());
        assertThat(service.open("open").freelancerName()).isEmpty();
    }
}
