package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.service.WeeklyDigestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The weekly digest's one-click unsubscribe (RFC 8058), posted by the mailbox without an account. */
@RestController
@RequestMapping("/public/weekly-digest")
@RequiredArgsConstructor
@Tag(name = "Weekly digest", description = "Stopping the weekly digest from its email")
public class PublicWeeklyDigestController {

    private final WeeklyDigestService digests;

    @Operation(summary = "Stop the weekly digest from the mailbox's one-click unsubscribe (RFC 8058)",
        description = "Posted by the mailbox with List-Unsubscribe=One-Click; answers 200 with no body")
    @PostMapping("/unsubscribe/{token}")
    public ResponseEntity<Void> unsubscribe(@PathVariable String token) {
        digests.unsubscribe(token);
        return ResponseEntity.ok().build();
    }
}
