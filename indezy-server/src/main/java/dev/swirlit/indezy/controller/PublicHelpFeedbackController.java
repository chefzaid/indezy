package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.service.HelpFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The help center's **Was this helpful?**, answered with an account or without one. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Help", description = "The help center's feedback")
public class PublicHelpFeedbackController {

    private final HelpFeedbackService feedback;

    /** Whether the article helped. */
    public record Answer(@NotNull Boolean helpful) {
    }

    @Operation(summary = "Say whether a help article helped", description = "Counted on the article's day, nothing about who answered")
    @PostMapping("/public/help/{slug}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void answer(@PathVariable String slug, @Valid @RequestBody Answer answer) {
        feedback.answer(slug, answer.helpful());
    }
}
