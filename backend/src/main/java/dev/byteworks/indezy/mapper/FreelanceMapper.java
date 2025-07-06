package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.FreelanceDto;
import dev.byteworks.indezy.model.Freelance;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface FreelanceMapper {

    @Mapping(target = "fullName", expression = "java(freelance.getFirstName() + \" \" + freelance.getLastName())")
    @Mapping(target = "totalProjects", expression = "java(freelance.getProjects().size())")
    @Mapping(target = "averageDailyRate", expression = "java(freelance.getProjects().stream().filter(p -> p.getDailyRate() != null).mapToInt(p -> p.getDailyRate()).average().orElse(0.0))")
    FreelanceDto toDto(Freelance freelance);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "clients", ignore = true)
    @Mapping(target = "contacts", ignore = true)
    @Mapping(target = "sources", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Freelance toEntity(FreelanceDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "clients", ignore = true)
    @Mapping(target = "contacts", ignore = true)
    @Mapping(target = "sources", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "cvFilePath", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(FreelanceDto dto, @MappingTarget Freelance freelance);
}
