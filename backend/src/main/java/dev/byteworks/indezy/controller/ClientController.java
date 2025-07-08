package dev.byteworks.indezy.controller;

import dev.byteworks.indezy.dto.ClientDto;
import dev.byteworks.indezy.service.ClientService;
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
@RequestMapping("/clients")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:4200", "http://127.0.0.1:4200"})
@Tag(name = "Clients", description = "Client management operations")
public class ClientController {

    private final ClientService clientService;

    @Operation(summary = "Get all clients", description = "Retrieve a list of all clients")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved clients",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ClientDto.class)))
    })
    @GetMapping
    public ResponseEntity<List<ClientDto>> getAllClients() {
        log.debug("GET /clients - Getting all clients");
        List<ClientDto> clients = clientService.findAll();
        return ResponseEntity.ok(clients);
    }

    @Operation(summary = "Get client by ID", description = "Retrieve a specific client by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved client",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ClientDto.class))),
        @ApiResponse(responseCode = "404", description = "Client not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ClientDto> getClientById(
            @Parameter(description = "Client ID", required = true) @PathVariable Long id) {
        log.debug("GET /clients/{} - Getting client by id", id);
        ClientDto client = clientService.findById(id);
        return ResponseEntity.ok(client);
    }

    @Operation(summary = "Create new client", description = "Create a new client with the provided details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Client created successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ClientDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid client data")
    })
    @PostMapping
    public ResponseEntity<ClientDto> createClient(
            @Parameter(description = "Client details", required = true) @Valid @RequestBody ClientDto clientDto) {
        log.debug("POST /clients - Creating new client");
        ClientDto createdClient = clientService.create(clientDto);
        return new ResponseEntity<>(createdClient, HttpStatus.CREATED);
    }

    @Operation(summary = "Update client", description = "Update an existing client with the provided details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Client updated successfully",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ClientDto.class))),
        @ApiResponse(responseCode = "404", description = "Client not found"),
        @ApiResponse(responseCode = "400", description = "Invalid client data")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ClientDto> updateClient(
            @Parameter(description = "Client ID", required = true) @PathVariable Long id,
            @Parameter(description = "Updated client details", required = true) @Valid @RequestBody ClientDto clientDto) {
        log.debug("PUT /clients/{} - Updating client", id);
        ClientDto updatedClient = clientService.update(id, clientDto);
        return ResponseEntity.ok(updatedClient);
    }

    @Operation(summary = "Delete client", description = "Delete a client by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Client deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Client not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(
            @Parameter(description = "Client ID", required = true) @PathVariable Long id) {
        log.debug("DELETE /clients/{} - Deleting client", id);
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get clients by freelance ID", description = "Retrieve all clients for a specific freelance")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved clients",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ClientDto.class)))
    })
    @GetMapping("/by-freelance/{freelanceId}")
    public ResponseEntity<List<ClientDto>> getClientsByFreelanceId(
            @Parameter(description = "Freelance ID", required = true) @PathVariable Long freelanceId) {
        log.debug("GET /clients/by-freelance/{} - Getting clients by freelance id", freelanceId);
        List<ClientDto> clients = clientService.findByFreelanceId(freelanceId);
        return ResponseEntity.ok(clients);
    }

    @Operation(summary = "Get client with projects", description = "Retrieve a client with all associated projects")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved client with projects",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ClientDto.class))),
        @ApiResponse(responseCode = "404", description = "Client not found")
    })
    @GetMapping("/{id}/with-projects")
    public ResponseEntity<ClientDto> getClientWithProjects(
            @Parameter(description = "Client ID", required = true) @PathVariable Long id) {
        log.debug("GET /clients/{}/with-projects - Getting client with projects", id);
        ClientDto client = clientService.findByIdWithProjects(id);
        return ResponseEntity.ok(client);
    }

    @Operation(summary = "Get client with contacts", description = "Retrieve a client with all associated contacts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved client with contacts",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ClientDto.class))),
        @ApiResponse(responseCode = "404", description = "Client not found")
    })
    @GetMapping("/{id}/with-contacts")
    public ResponseEntity<ClientDto> getClientWithContacts(
            @Parameter(description = "Client ID", required = true) @PathVariable Long id) {
        log.debug("GET /clients/{}/with-contacts - Getting client with contacts", id);
        ClientDto client = clientService.findByIdWithContacts(id);
        return ResponseEntity.ok(client);
    }

    @Operation(summary = "Get clients by final status", description = "Filter clients by their final status")
    @GetMapping("/by-freelance/{freelanceId}/final/{isFinal}")
    public ResponseEntity<List<ClientDto>> getClientsByFinalStatus(
            @PathVariable Long freelanceId,
            @PathVariable Boolean isFinal) {
        log.debug("GET /clients/by-freelance/{}/final/{} - Getting clients by final status", freelanceId, isFinal);
        List<ClientDto> clients = clientService.findByFreelanceIdAndIsFinal(freelanceId, isFinal);
        return ResponseEntity.ok(clients);
    }

    @Operation(summary = "Search clients by company name", description = "Search clients by company name containing the given text")
    @GetMapping("/by-freelance/{freelanceId}/search")
    public ResponseEntity<List<ClientDto>> searchClientsByCompanyName(
            @PathVariable Long freelanceId,
            @RequestParam String companyName) {
        log.debug("GET /clients/by-freelance/{}/search?companyName={} - Searching clients", freelanceId, companyName);
        List<ClientDto> clients = clientService.findByFreelanceIdAndCompanyNameContaining(freelanceId, companyName);
        return ResponseEntity.ok(clients);
    }

    @Operation(summary = "Get clients by city", description = "Filter clients by city")
    @GetMapping("/by-freelance/{freelanceId}/city/{city}")
    public ResponseEntity<List<ClientDto>> getClientsByCity(
            @PathVariable Long freelanceId,
            @PathVariable String city) {
        log.debug("GET /clients/by-freelance/{}/city/{} - Getting clients by city", freelanceId, city);
        List<ClientDto> clients = clientService.findByFreelanceIdAndCity(freelanceId, city);
        return ResponseEntity.ok(clients);
    }

    @Operation(summary = "Get distinct cities", description = "Get all unique cities for a freelance's clients")
    @GetMapping("/by-freelance/{freelanceId}/cities")
    public ResponseEntity<List<String>> getDistinctCities(@PathVariable Long freelanceId) {
        log.debug("GET /clients/by-freelance/{}/cities - Getting distinct cities", freelanceId);
        List<String> cities = clientService.findDistinctCitiesByFreelanceId(freelanceId);
        return ResponseEntity.ok(cities);
    }

    @Operation(summary = "Get distinct domains", description = "Get all unique domains for a freelance's clients")
    @GetMapping("/by-freelance/{freelanceId}/domains")
    public ResponseEntity<List<String>> getDistinctDomains(@PathVariable Long freelanceId) {
        log.debug("GET /clients/by-freelance/{}/domains - Getting distinct domains", freelanceId);
        List<String> domains = clientService.findDistinctDomainsByFreelanceId(freelanceId);
        return ResponseEntity.ok(domains);
    }
}
