package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.WebhookDtos;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The workspace's webhooks: under /users, so an API token, which reads the workspace, cannot change them. */
@RestController
@RequestMapping("/users/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Addresses told of the workspace's events, signed with their secret")
public class WebhookController {

    private final WebhookService webhooks;
    private final AccessGuard accessGuard;

    @Operation(summary = "The workspace's webhooks")
    @GetMapping
    public List<WebhookDtos.Webhook> list() {
        return webhooks.list(workspace());
    }

    @Operation(summary = "Add a webhook", description = "The secret of its signatures is in the answer, this once only")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WebhookDtos.Created create(@Valid @RequestBody WebhookDtos.Write write) {
        return webhooks.create(workspace(), write);
    }

    @Operation(summary = "Change a webhook's address or events, or turn it on or off")
    @PutMapping("/{id}")
    public WebhookDtos.Webhook update(@PathVariable Long id, @Valid @RequestBody WebhookDtos.Write write) {
        return webhooks.update(workspace(), id, write);
    }

    @Operation(summary = "Delete a webhook and its deliveries")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        webhooks.delete(workspace(), id);
    }

    @Operation(summary = "Send a test event to a webhook")
    @PostMapping("/{id}/ping")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WebhookDtos.Delivery ping(@PathVariable Long id) {
        return webhooks.ping(workspace(), id);
    }

    @Operation(summary = "A webhook's latest deliveries, the newest first")
    @GetMapping("/{id}/deliveries")
    public List<WebhookDtos.Delivery> deliveries(@PathVariable Long id) {
        return webhooks.deliveries(workspace(), id);
    }

    private Long workspace() {
        return accessGuard.requireWorkspace(null);
    }
}
