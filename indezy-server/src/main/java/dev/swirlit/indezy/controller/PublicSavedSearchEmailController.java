package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.PublicSavedSearchEmailsDto;
import dev.swirlit.indezy.service.SavedSearchEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The unsubscribe links of the saved search emails, used without an account. The page's link keeps its token after
 * {@code #} and sends it in the {@value #TOKEN_HEADER} header; a mailbox's one-click unsubscribe (RFC 8058) posts to an
 * address holding it, as the standard requires. A token can only show a search's name and stop its emails.
 */
@RestController
@RequestMapping("/public/saved-search-emails")
@RequiredArgsConstructor
@Tag(name = "Saved search emails", description = "Unsubscribing from a saved search's emails through their link")
public class PublicSavedSearchEmailController {

    static final String TOKEN_HEADER = "X-Email-Token";
    private static final String TOKEN_DESCRIPTION = "Token of the unsubscribe link, from the part of the link after #";

    private final SavedSearchEmailService emails;

    @Operation(summary = "Show the search an unsubscribe link stops the emails of")
    @GetMapping
    public PublicSavedSearchEmailsDto describe(@Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(TOKEN_HEADER) String token) {
        return emails.describe(token);
    }

    @Operation(summary = "Stop the search's emails")
    @PostMapping("/unsubscribe")
    public PublicSavedSearchEmailsDto unsubscribe(@Parameter(description = TOKEN_DESCRIPTION) @RequestHeader(TOKEN_HEADER) String token) {
        return emails.unsubscribe(token);
    }

    @Operation(summary = "Stop the search's emails from the mailbox's one-click unsubscribe (RFC 8058)",
        description = "Posted by the mailbox with List-Unsubscribe=One-Click; answers 200 with no body")
    @PostMapping("/unsubscribe/{token}")
    public ResponseEntity<Void> unsubscribeInOneClick(@PathVariable String token) {
        emails.unsubscribe(token);
        return ResponseEntity.ok().build();
    }
}
