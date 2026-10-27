package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ContactRelationDto;
import dev.swirlit.indezy.dto.ContactRelationRequest;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ContactRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Contacts", description = "People at clients and ESNs")
public class ContactRelationController {

    private final ContactRelationService relationService;
    private final AccessGuard accessGuard;

    @Operation(summary = "List the people a contact works with")
    @GetMapping("/contacts/{contactId}/relations")
    public List<ContactRelationDto> list(@PathVariable Long contactId) {
        accessGuard.requireContact(contactId);
        return relationService.list(contactId);
    }

    @Operation(summary = "List whom the contacts of a client work with, for its org chart")
    @GetMapping("/clients/{clientId}/contact-relations")
    public List<ContactRelationDto> listForClient(@PathVariable Long clientId) {
        accessGuard.requireClient(clientId);
        return relationService.listForClient(clientId);
    }

    @Operation(summary = "Link two contacts who work together, or change how")
    @PutMapping("/contacts/{contactId}/relations")
    public ContactRelationDto link(@PathVariable Long contactId, @Valid @RequestBody ContactRelationRequest request) {
        accessGuard.requireContact(contactId);
        accessGuard.requireContact(request.otherId());
        return relationService.link(contactId, request);
    }

    @Operation(summary = "Unlink two contacts")
    @DeleteMapping("/contacts/{contactId}/relations/{otherId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> unlink(@PathVariable Long contactId, @PathVariable Long otherId) {
        accessGuard.requireContact(contactId);
        relationService.unlink(contactId, otherId);
        return ResponseEntity.noContent().build();
    }
}
