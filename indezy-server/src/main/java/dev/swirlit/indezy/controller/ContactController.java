package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.BulkDeleteRequest;
import dev.swirlit.indezy.dto.BulkDeleteResult;
import dev.swirlit.indezy.dto.ContactDto;
import dev.swirlit.indezy.dto.ContactImportRequest;
import dev.swirlit.indezy.dto.ContactImportResultDto;
import dev.swirlit.indezy.dto.ContactPersonalDataDto;
import dev.swirlit.indezy.util.SecurityUtils;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ContactImportService;
import dev.swirlit.indezy.service.ContactPrivacyService;
import dev.swirlit.indezy.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
    private final ContactPrivacyService contactPrivacyService;
    private final AccessGuard accessGuard;

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
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ContactDto> createContact(@Valid @RequestBody ContactDto contactDto) {
        contactDto.setFreelanceId(accessGuard.resolveFreelanceId(contactDto.getFreelanceId()));
        accessGuard.requireClient(contactDto.getClientId());
        accessGuard.requireContactIfPresent(contactDto.getManagerId());
        return new ResponseEntity<>(contactService.create(contactDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Update a contact")
    @PutMapping("/{id}")
    public ContactDto updateContact(@PathVariable Long id, @Valid @RequestBody ContactDto contactDto) {
        accessGuard.requireContact(id);
        contactDto.setFreelanceId(accessGuard.resolveFreelanceId(contactDto.getFreelanceId()));
        accessGuard.requireClientIfPresent(contactDto.getClientId());
        accessGuard.requireContactIfPresent(contactDto.getManagerId());
        return contactService.update(id, contactDto);
    }

    @Operation(summary = "Delete a contact")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
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

    @Operation(summary = "Delete several contacts, with their exchanges")
    @PostMapping("/bulk-delete")
    public BulkDeleteResult deleteAll(@Valid @RequestBody BulkDeleteRequest request) {
        request.ids().forEach(accessGuard::requireContact);
        return contactService.deleteAll(request.ids());
    }

    @Operation(summary = "What the workspace holds about a contact, to hand them on request (GDPR access)")
    @GetMapping("/{id}/personal-data")
    public ResponseEntity<ContactPersonalDataDto> personalData(@PathVariable Long id) {
        accessGuard.requireContact(id);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("contact-" + id + ".json").build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(contactPrivacyService.personalData(id, SecurityUtils.getCurrentUserId()));
    }

    @Operation(summary = "Erase a contact on their request (GDPR erasure), recorded in the account's activity")
    @PostMapping("/{id}/erase")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void erase(@PathVariable Long id) {
        accessGuard.requireContact(id);
        contactPrivacyService.erase(id, SecurityUtils.getCurrentUserId());
    }
}
