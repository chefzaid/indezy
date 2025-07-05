package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.ClientDto;
import dev.byteworks.indezy.model.Client;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

    public ClientDto toDto(Client client) {
        if (client == null) {
            return null;
        }

        ClientDto dto = new ClientDto();
        dto.setId(client.getId());
        dto.setCompanyName(client.getCompanyName());
        dto.setAddress(client.getAddress());
        dto.setCity(client.getCity());
        dto.setDomain(client.getDomain());
        dto.setIsFinal(client.getIsFinal());
        dto.setNotes(client.getNotes());
        dto.setCreatedAt(client.getCreatedAt());
        dto.setUpdatedAt(client.getUpdatedAt());
        
        if (client.getFreelance() != null) {
            dto.setFreelanceId(client.getFreelance().getId());
        }

        // Computed fields
        if (client.getProjects() != null) {
            dto.setTotalProjects(client.getProjects().size());
            dto.setAverageDailyRate(
                client.getProjects().stream()
                    .filter(p -> p.getDailyRate() != null)
                    .mapToInt(p -> p.getDailyRate())
                    .average()
                    .orElse(0.0)
            );
            // Note: Projects collection mapping would be handled by ProjectMapper if needed
            dto.setProjects(new java.util.ArrayList<>());
        }

        if (client.getContacts() != null) {
            dto.setTotalContacts(client.getContacts().size());
            // Note: Contacts collection mapping would be handled by ContactMapper if needed
            dto.setContacts(new java.util.ArrayList<>());
        }

        return dto;
    }

    public Client toEntity(ClientDto dto) {
        if (dto == null) {
            return null;
        }

        Client client = new Client();
        client.setId(dto.getId());
        client.setCompanyName(dto.getCompanyName());
        client.setAddress(dto.getAddress());
        client.setCity(dto.getCity());
        client.setDomain(dto.getDomain());
        client.setIsFinal(dto.getIsFinal());
        client.setNotes(dto.getNotes());

        return client;
    }

    public void updateEntity(ClientDto dto, Client client) {
        if (dto == null || client == null) {
            return;
        }

        client.setCompanyName(dto.getCompanyName());
        client.setAddress(dto.getAddress());
        client.setCity(dto.getCity());
        client.setDomain(dto.getDomain());
        client.setIsFinal(dto.getIsFinal());
        client.setNotes(dto.getNotes());
    }
}
