package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ContactInteractionDto;
import dev.swirlit.indezy.dto.ContactInteractionsDto;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ContactInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contacts/{contactId}/interactions")
@RequiredArgsConstructor
@Tag(name = "Contacts", description = "People at clients and ESNs")
public class ContactInteractionController {

    private final ContactInteractionService interactionService;
    private final AccessGuard accessGuard;

    @Operation(summary = "List a contact's exchanges, with its last contact, response rate and preferred channel")
    @GetMapping
    public ContactInteractionsDto list(@PathVariable Long contactId) {
        accessGuard.requireContact(contactId);
        return interactionService.list(contactId);
    }

    @Operation(summary = "Record an exchange with a contact")
    @PostMapping
    public ResponseEntity<ContactInteractionDto> create(@PathVariable Long contactId,
                                                        @Valid @RequestBody ContactInteractionDto request) {
        accessGuard.requireContact(contactId);
        return new ResponseEntity<>(interactionService.create(contactId, request), HttpStatus.CREATED);
    }

    @Operation(summary = "Change an exchange with a contact")
    @PutMapping("/{id}")
    public ContactInteractionDto update(@PathVariable Long contactId, @PathVariable Long id,
                                        @Valid @RequestBody ContactInteractionDto request) {
        accessGuard.requireContact(contactId);
        return interactionService.update(contactId, id, request);
    }

    @Operation(summary = "Delete an exchange with a contact")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long contactId, @PathVariable Long id) {
        accessGuard.requireContact(contactId);
        interactionService.delete(contactId, id);
        return ResponseEntity.noContent().build();
    }
}
