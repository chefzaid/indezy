package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.ClientDto;
import dev.byteworks.indezy.model.Client;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ClientMapper {

    @Mapping(target = "freelanceId", source = "freelance.id")
    @Mapping(target = "totalProjects", expression = "java(client.getProjects().size())")
    @Mapping(target = "averageDailyRate", expression = "java(client.getProjects().stream().filter(p -> p.getDailyRate() != null).mapToInt(p -> p.getDailyRate()).average().orElse(0.0))")
    @Mapping(target = "totalContacts", expression = "java(client.getContacts().size())")
    @Mapping(target = "projects", expression = "java(new java.util.ArrayList<>())")
    @Mapping(target = "contacts", expression = "java(new java.util.ArrayList<>())")
    ClientDto toDto(Client client);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "freelance", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "contacts", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Client toEntity(ClientDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "freelance", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "contacts", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(ClientDto dto, @MappingTarget Client client);
}
