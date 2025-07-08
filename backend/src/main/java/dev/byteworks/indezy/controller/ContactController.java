package dev.byteworks.indezy.controller;

import dev.byteworks.indezy.dto.ContactDto;
import dev.byteworks.indezy.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/contacts")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
@Tag(name = "Contacts", description = "Contact management operations")
public class ContactController {

    private final ContactService contactService;

    @Operation(summary = "Get all contacts", description = "Retrieve a list of all contacts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved contacts",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContactDto.class)))
    })
    @GetMapping
    public ResponseEntity<List<ContactDto>> getAllContacts() {
        log.debug("GET /contacts - Getting all contacts");
        List<ContactDto> contacts = contactService.findAll();
        return ResponseEntity.ok(contacts);
    }

    @Operation(summary = "Get contact by ID", description = "Retrieve a specific contact by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved contact",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContactDto.class))),
        @ApiResponse(responseCode = "404", description = "Contact not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ContactDto> getContactById(
            @Parameter(description = "Contact ID", required = true) @PathVariable Long id) {
        log.debug("GET /contacts/{} - Getting contact by id", id);
        ContactDto contact = contactService.findById(id);
        return ResponseEntity.ok(contact);
    }

    @Operation(summary = "Create new contact", description = "Create a new contact with the provided details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Contact created successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContactDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid contact data")
    })
    @PostMapping
    public ResponseEntity<ContactDto> createContact(
            @Parameter(description = "Contact details", required = true) @Valid @RequestBody ContactDto contactDto) {
        log.debug("POST /contacts - Creating new contact");
        ContactDto createdContact = contactService.create(contactDto);
        return new ResponseEntity<>(createdContact, HttpStatus.CREATED);
    }

    @Operation(summary = "Update contact", description = "Update an existing contact with the provided details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Contact updated successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContactDto.class))),
        @ApiResponse(responseCode = "404", description = "Contact not found"),
        @ApiResponse(responseCode = "400", description = "Invalid contact data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ContactDto> updateContact(
            @Parameter(description = "Contact ID", required = true) @PathVariable Long id,
            @Parameter(description = "Updated contact details", required = true) @Valid @RequestBody ContactDto contactDto) {
        log.debug("PUT /contacts/{} - Updating contact", id);
        ContactDto updatedContact = contactService.update(id, contactDto);
        return ResponseEntity.ok(updatedContact);
    }

    @Operation(summary = "Delete contact", description = "Delete a contact by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Contact deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Contact not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContact(
            @Parameter(description = "Contact ID", required = true) @PathVariable Long id) {
        log.debug("DELETE /contacts/{} - Deleting contact", id);
        contactService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get contacts by freelance ID", description = "Retrieve all contacts for a specific freelance")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved contacts",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContactDto.class)))
    })
    @GetMapping("/by-freelance/{freelanceId}")
    public ResponseEntity<List<ContactDto>> getContactsByFreelanceId(
            @Parameter(description = "Freelance ID", required = true) @PathVariable Long freelanceId) {
        log.debug("GET /contacts/by-freelance/{} - Getting contacts by freelance id", freelanceId);
        List<ContactDto> contacts = contactService.findByFreelanceId(freelanceId);
        return ResponseEntity.ok(contacts);
    }

    @Operation(summary = "Get contacts by client ID", description = "Retrieve all contacts for a specific client")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved contacts",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContactDto.class)))
    })
    @GetMapping("/by-client/{clientId}")
    public ResponseEntity<List<ContactDto>> getContactsByClientId(
            @Parameter(description = "Client ID", required = true) @PathVariable Long clientId) {
        log.debug("GET /contacts/by-client/{} - Getting contacts by client id", clientId);
        List<ContactDto> contacts = contactService.findByClientId(clientId);
        return ResponseEntity.ok(contacts);
    }

    @Operation(summary = "Search contacts by name", description = "Search contacts by name for a specific freelance")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved contacts",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContactDto.class)))
    })
    @GetMapping("/by-freelance/{freelanceId}/search/name")
    public ResponseEntity<List<ContactDto>> searchContactsByName(
            @Parameter(description = "Freelance ID", required = true) @PathVariable Long freelanceId,
            @Parameter(description = "Name to search for", required = true) @RequestParam String name) {
        log.debug("GET /contacts/by-freelance/{}/search/name?name={} - Searching contacts by name", freelanceId, name);
        List<ContactDto> contacts = contactService.searchByName(freelanceId, name);
        return ResponseEntity.ok(contacts);
    }

    @Operation(summary = "Search contacts by email", description = "Search contacts by email for a specific freelance")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved contacts",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContactDto.class)))
    })
    @GetMapping("/by-freelance/{freelanceId}/search/email")
    public ResponseEntity<List<ContactDto>> searchContactsByEmail(
            @Parameter(description = "Freelance ID", required = true) @PathVariable Long freelanceId,
            @Parameter(description = "Email to search for", required = true) @RequestParam String email) {
        log.debug("GET /contacts/by-freelance/{}/search/email?email={} - Searching contacts by email", freelanceId, email);
        List<ContactDto> contacts = contactService.searchByEmail(freelanceId, email);
        return ResponseEntity.ok(contacts);
    }
}
