package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.TestimonialDtos;
import dev.swirlit.indezy.mail.MailException;
import dev.swirlit.indezy.mail.ResendMailer;
import dev.swirlit.indezy.mail.TestimonialRequestEmailComposer;
import dev.swirlit.indezy.model.Freelance;
import dev.swirlit.indezy.model.enums.DocumentLanguage;
import dev.swirlit.indezy.repository.FreelanceRepository;
import dev.swirlit.indezy.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Emails a testimonial's link to the person asked, once it is saved and outside its transaction: an email that cannot go
 * leaves the request and its link as they are, for the freelancer to give by hand.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TestimonialRequestEmails {

    private final ResendMailer mailer;
    private final TestimonialRequestEmailComposer composer;
    private final FreelanceRepository freelances;
    private final UserRepository users;

    /** The link, saying whether it went by email. */
    public TestimonialDtos.Link send(Long freelanceId, TestimonialDtos.Link link) {
        TestimonialDtos.Testimonial testimonial = link.testimonial();
        if (testimonial.recipientEmail() == null || !mailer.configured()) {
            return link;
        }
        Freelance freelance = freelances.findById(freelanceId).orElse(null);
        if (freelance == null) {
            return link;
        }
        String name = Stream.of(freelance.getFirstName(), freelance.getLastName()).filter(Objects::nonNull).map(String::strip)
            .filter(part -> !part.isEmpty()).collect(Collectors.joining(" "));
        DocumentLanguage language = users.findByEmail(freelance.getEmail())
            .filter(user -> "en".equalsIgnoreCase(user.getLanguagePreference())).map(user -> DocumentLanguage.EN).orElse(DocumentLanguage.FR);
        try {
            mailer.send(composer.compose(testimonial.recipientName(), testimonial.recipientEmail(), name, language, link.token(),
                testimonial.expiresAt(), "testimonial-request-" + testimonial.id()));
            log.info("Emailed testimonial {} to its recipient", testimonial.id());
            return link.emailed(true);
        } catch (MailException e) {
            log.warn("The email of testimonial {} was not sent: {}", testimonial.id(), e.getMessage());
            return link;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return link;
        }
    }
}
