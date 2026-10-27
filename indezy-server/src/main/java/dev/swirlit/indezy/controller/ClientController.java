package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.BulkDeleteRequest;
import dev.swirlit.indezy.dto.BulkDeleteResult;
import dev.swirlit.indezy.dto.ClientDto;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Final clients and intermediaries (ESNs)")
public class ClientController {

    private final ClientService clientService;
    private final AccessGuard accessGuard;

    @Operation(summary = "List the clients of a workspace")
    @GetMapping("/by-freelance/{freelanceId}")
    public List<ClientDto> getClientsByFreelanceId(@PathVariable Long freelanceId) {
        accessGuard.requireFreelance(freelanceId);
        return clientService.findByFreelanceId(freelanceId);
    }

    @Operation(summary = "Get a client")
    @GetMapping("/{id}")
    public ClientDto getClientById(@PathVariable Long id) {
        accessGuard.requireClient(id);
        return clientService.findById(id);
    }

    @Operation(summary = "Create a client")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ClientDto> createClient(@Valid @RequestBody ClientDto clientDto) {
        clientDto.setFreelanceId(accessGuard.resolveFreelanceId(clientDto.getFreelanceId()));
        return new ResponseEntity<>(clientService.create(clientDto), HttpStatus.CREATED);
    }

    @Operation(summary = "Update a client")
    @PutMapping("/{id}")
    public ClientDto updateClient(@PathVariable Long id, @Valid @RequestBody ClientDto clientDto) {
        accessGuard.requireClient(id);
        clientDto.setFreelanceId(accessGuard.resolveFreelanceId(clientDto.getFreelanceId()));
        return clientService.update(id, clientDto);
    }

    @Operation(summary = "Delete a client", description = "Refused (409) while projects still reference it")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteClient(@PathVariable Long id) {
        accessGuard.requireClient(id);
        clientService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete several clients, keeping those still linked to missions")
    @PostMapping("/bulk-delete")
    public BulkDeleteResult deleteAll(@Valid @RequestBody BulkDeleteRequest request) {
        request.ids().forEach(accessGuard::requireClient);
        return clientService.deleteAll(request.ids());
    }
}
