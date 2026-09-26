package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.ContactDto;
import dev.swirlit.indezy.dto.ContactImportRequest;
import dev.swirlit.indezy.dto.ContactImportResultDto;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ContactImportService;
import dev.swirlit.indezy.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contacts")
@RequiredArgsConstructor
@Tag(name = "Contacts", description = "People at clients and ESNs")
public class ContactController {

    private final ContactService contactService;
    private final ContactImportService contactImportService;
    private final AccessGuard accessGuard;

    @Operation(summary = "List the caller's contacts")
    @GetMapping
    public List<ContactDto> getAllContacts() {
        return accessGuard.currentFreelanceId()
            .map(contactService::findByFreelanceId)
            .orElseGet(contactService::findAll);
    }

    @Operation(summary = "List the contacts of a workspace")
    @GetMapping("/by-freelance/{freelanceId}")
    public List<ContactDto> getContactsByFreelanceId(@PathVariable Long freelanceId) {
        accessGuard.requireFreelance(freelanceId);
        return contactService.findByFreelanceId(freelanceId);
    }

    @Operation(summary = "List the contacts of a client")
    @GetMapping("/by-client/{clientId}")
    public List<ContactDto> getContactsByClientId(@PathVariable Long clientId) {
        accessGuard.requireClient(clientId);
        return contactService.findByClientId(clientId);
    }

    @Operation(summary = "Get a contact")
    @GetMapping("/{id}")
    public ContactDto getContactById(@PathVariable Long id) {
        accessGuard.requireContact(id);
        return contactService.findById(id);
    }

    @Operation(summary = "Create a contact")
    @PostMapping
    public ResponseEntity<ContactDto> createContact(@Valid @RequestBody ContactDto contactDto) {
        contactDto.setFreelanceId(accessGuard.resolveFreelanceId(contactDto.getFreelanceId()));
        accessGuard.requireClient(contactDto.getClientId());
        return new ResponseEntity<>(contactService.create(contactDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Update a contact")
    @PutMapping("/{id}")
    public ContactDto updateContact(@PathVariable Long id, @Valid @RequestBody ContactDto contactDto) {
        accessGuard.requireContact(id);
        contactDto.setFreelanceId(accessGuard.resolveFreelanceId(contactDto.getFreelanceId()));
        accessGuard.requireClientIfPresent(contactDto.getClientId());
        return contactService.update(id, contactDto);
    }

    @Operation(summary = "Delete a contact")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContact(@PathVariable Long id) {
        accessGuard.requireContact(id);
        contactService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Import contacts for a client",
        description = "Parses a CSV or vCard payload and imports its contacts under the given client")
    @PostMapping("/import/by-client/{clientId}")
    public ContactImportResultDto importContacts(@PathVariable Long clientId,
                                                 @Valid @RequestBody ContactImportRequest request) {
        accessGuard.requireClient(clientId);
        return contactImportService.importForClient(clientId, request.getContent());
    }
}
