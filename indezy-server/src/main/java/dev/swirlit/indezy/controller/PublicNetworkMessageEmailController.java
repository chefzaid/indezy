package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.service.NetworkMessageEmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The unread messages email's one-click unsubscribe (RFC 8058), posted by the mailbox without an account. */
@RestController
@RequestMapping("/public/network-messages")
@RequiredArgsConstructor
@Tag(name = "Network messages email", description = "Stopping the unread messages emails from one of them")
public class PublicNetworkMessageEmailController {

    private final NetworkMessageEmailService emails;

    @Operation(summary = "Stop the unread messages emails from the mailbox's one-click unsubscribe (RFC 8058)",
        description = "Posted by the mailbox with List-Unsubscribe=One-Click; answers 200 with no body")
    @PostMapping("/unsubscribe/{token}")
    public ResponseEntity<Void> unsubscribe(@PathVariable String token) {
        emails.unsubscribe(token);
        return ResponseEntity.ok().build();
    }
}
