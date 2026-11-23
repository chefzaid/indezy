package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.OfferWordSuggestionDto;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.OfferWordSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Sector words the caller's workspace keeps hiding offers for. {@code freelanceId} is only read when security checks are off (tests). */
@RestController
@RequestMapping("/offers/hidden-words")
@RequiredArgsConstructor
@Tag(name = "Offers", description = "Freelance missions collected from the job boards")
public class OfferWordSuggestionController {

    private final OfferWordSuggestionService words;
    private final AccessGuard accessGuard;

    @Operation(summary = "The sector words the workspace's hidden offers keep mentioning, offered to leave out of its feed",
        description = "Five hidden offers in 30 days or more, a quarter of them at least, none kept nor applied to; two at most")
    @GetMapping("/suggestions")
    public List<OfferWordSuggestionDto> suggestions(@RequestParam(required = false) Long freelanceId) {
        return words.suggestions(accessGuard.requireWorkspace(freelanceId));
    }
}
