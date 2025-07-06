package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.SourceDto;
import dev.byteworks.indezy.model.Source;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface SourceMapper {

    @Mapping(target = "freelanceId", source = "freelance.id")
    @Mapping(target = "totalProjects", expression = "java(source.getProjects().size())")
    @Mapping(target = "averageDailyRate", expression = "java(source.getProjects().stream().filter(p -> p.getDailyRate() != null).mapToInt(p -> p.getDailyRate()).average().orElse(0.0))")
    @Mapping(target = "projects", expression = "java(new java.util.ArrayList<>())")
    SourceDto toDto(Source source);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "freelance", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Source toEntity(SourceDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "freelance", ignore = true)
    @Mapping(target = "projects", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(SourceDto dto, @MappingTarget Source source);
}
