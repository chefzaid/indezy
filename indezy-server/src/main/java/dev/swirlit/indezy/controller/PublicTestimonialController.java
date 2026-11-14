package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.TestimonialDtos;
import dev.swirlit.indezy.service.TestimonialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A testimonial's author, without an account: the link's token comes from the part of the link after #, so it never
 * reaches a server log, and is sent in a header.
 */
@RestController
@RequestMapping("/public/testimonials")
@RequiredArgsConstructor
@Tag(name = "Testimonial writing", description = "A testimonial written through its link, without an account")
public class PublicTestimonialController {

    static final String TOKEN_HEADER = "X-Testimonial-Token";
    private static final String TOKEN_DESCRIPTION = "Token of the testimonial link, from the part of the link after #";

    private final TestimonialService testimonials;

    @Operation(summary = "Who asks the testimonial, and whether it was already written")
    @GetMapping
    public TestimonialDtos.PublicRequest open(@Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(TOKEN_HEADER) String token) {
        return testimonials.open(token);
    }

    @Operation(summary = "Write the testimonial", description = "Once, its author agreeing to be named with it on the public profile")
    @PostMapping
    public TestimonialDtos.PublicRequest answer(@Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(TOKEN_HEADER) String token,
                                                @Valid @RequestBody TestimonialDtos.Answer answer) {
        return testimonials.answer(token, answer);
    }
}
