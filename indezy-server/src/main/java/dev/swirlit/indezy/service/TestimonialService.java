package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.TestimonialDtos;
import dev.swirlit.indezy.exception.InvalidStateException;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.model.Contact;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.Testimonial;
import dev.swirlit.indezy.model.enums.TestimonialStatus;
import dev.swirlit.indezy.repository.ContactRepository;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.TestimonialRepository;
import dev.swirlit.indezy.util.Tokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Testimonials (see docs/features.md#testimonials): the freelancer asks a contact through a link, the author writes
 * once without an account and agrees to be named, and the freelancer shows it on the public profile or keeps it hidden.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestimonialService {

    static final Duration LINK_VALIDITY = Duration.ofDays(30);
    /** Requests not yet answered a workspace may have at once. */
    static final int MAX_OPEN_REQUESTS = 50;
    private static final int MAX_TOKEN_LENGTH = 128;
    private static final String LINK_INVALID = "This testimonial link is not valid or has expired";

    private final TestimonialRepository testimonials;
    private final ContactRepository contacts;
    private final FreelanceRepository freelances;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TestimonialDtos.Testimonial> list(Long freelanceId) {
        return testimonials.findByFreelanceIdOrderByIdDesc(freelanceId).stream().map(TestimonialService::dto).toList();
    }

    /** Asks a testimonial of a contact of the workspace (checked by the caller) or of someone named; the token is shown once. */
    @Transactional
    public TestimonialDtos.Link request(Long freelanceId, TestimonialDtos.Request request) {
        if (testimonials.countByFreelanceIdAndStatus(freelanceId, TestimonialStatus.REQUESTED) >= MAX_OPEN_REQUESTS) {
            throw new InvalidStateException("At most " + MAX_OPEN_REQUESTS + " testimonials can wait for their author at once");
        }
        String recipient = request.contactId() != null ?
            contacts.findById(request.contactId()).map(TestimonialService::name).orElseThrow(() -> new ResourceNotFoundException("Contact not found")) :
            blankToNull(request.recipientName());
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("A testimonial is asked of a contact or of someone named");
        }
        String token = Tokens.random();
        Testimonial testimonial = new Testimonial();
        testimonial.setFreelanceId(freelanceId);
        testimonial.setContactId(request.contactId());
        testimonial.setRecipientName(recipient);
        testimonial.setTokenHash(Tokens.sha256Hex(token));
        testimonial.setExpiresAt(clock.instant().plus(LINK_VALIDITY));
        Testimonial saved = testimonials.save(testimonial);
        log.info("Asked testimonial {}", saved.getId());
        return new TestimonialDtos.Link(token, dto(saved));
    }

    /** Shows a received testimonial on the public profile, or hides it; one not yet written can be neither. */
    @Transactional
    public TestimonialDtos.Testimonial setStatus(Long freelanceId, Long id, TestimonialStatus status) {
        if (status != TestimonialStatus.APPROVED && status != TestimonialStatus.HIDDEN) {
            throw new IllegalArgumentException("A testimonial is approved or hidden");
        }
        Testimonial testimonial = owned(freelanceId, id);
        if (testimonial.getStatus() == TestimonialStatus.REQUESTED) {
            throw new InvalidStateException("This testimonial has not been written yet");
        }
        testimonial.setStatus(status);
        return dto(testimonials.save(testimonial));
    }

    @Transactional
    public void delete(Long freelanceId, Long id) {
        testimonials.delete(owned(freelanceId, id));
    }

    /** What the author sees behind the link. */
    @Transactional(readOnly = true)
    public TestimonialDtos.PublicRequest open(String token) {
        Testimonial testimonial = byToken(token);
        return new TestimonialDtos.PublicRequest(freelancerName(testimonial.getFreelanceId()), testimonial.getRecipientName(),
            testimonial.getStatus() != TestimonialStatus.REQUESTED);
    }

    /** The author writes the testimonial, once; the freelancer then decides whether to show it. */
    @Transactional
    public TestimonialDtos.PublicRequest answer(String token, TestimonialDtos.Answer answer) {
        Testimonial testimonial = byToken(token);
        if (testimonial.getStatus() != TestimonialStatus.REQUESTED) {
            throw new InvalidStateException("This testimonial has already been written");
        }
        testimonial.setAuthorName(answer.authorName().strip());
        testimonial.setAuthorTitle(blankToNull(answer.authorTitle()));
        testimonial.setText(answer.text().strip());
        testimonial.setStatus(TestimonialStatus.RECEIVED);
        testimonial.setReceivedAt(clock.instant());
        testimonials.save(testimonial);
        log.info("Received testimonial {}", testimonial.getId());
        return new TestimonialDtos.PublicRequest(freelancerName(testimonial.getFreelanceId()), testimonial.getRecipientName(), true);
    }

    /** The approved testimonials, for the public profile, the latest first. */
    @Transactional(readOnly = true)
    public List<TestimonialDtos.Shown> approved(Long freelanceId) {
        return testimonials.findByFreelanceIdAndStatusOrderByReceivedAtDesc(freelanceId, TestimonialStatus.APPROVED).stream()
            .map(testimonial -> new TestimonialDtos.Shown(testimonial.getAuthorName(), testimonial.getAuthorTitle(), testimonial.getText()))
            .toList();
    }

    private Testimonial owned(Long freelanceId, Long id) {
        return testimonials.findByIdAndFreelanceId(id, freelanceId).orElseThrow(() -> new ResourceNotFoundException("Testimonial not found"));
    }

    private Testimonial byToken(String token) {
        if (token == null || token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            throw new ResourceNotFoundException(LINK_INVALID);
        }
        return testimonials.findByTokenHash(Tokens.sha256Hex(token.strip()))
            .filter(testimonial -> testimonial.getExpiresAt().isAfter(clock.instant()) || testimonial.getStatus() != TestimonialStatus.REQUESTED)
            .orElseThrow(() -> new ResourceNotFoundException(LINK_INVALID));
    }

    private String freelancerName(Long freelanceId) {
        return freelances.findById(freelanceId).map(TestimonialService::name).orElse("");
    }

    private static String name(Contact contact) {
        return join(contact.getFirstName(), contact.getLastName());
    }

    private static String name(Freelance freelance) {
        return join(freelance.getFirstName(), freelance.getLastName());
    }

    private static String join(String first, String last) {
        return String.join(" ", Stream.of(first, last).filter(Objects::nonNull).map(String::strip).filter(part -> !part.isEmpty()).toList());
    }

    private static TestimonialDtos.Testimonial dto(Testimonial testimonial) {
        return new TestimonialDtos.Testimonial(testimonial.getId(), testimonial.getContactId(), testimonial.getRecipientName(),
            testimonial.getStatus(), testimonial.getExpiresAt(), testimonial.getAuthorName(), testimonial.getAuthorTitle(),
            testimonial.getText(), testimonial.getReceivedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
