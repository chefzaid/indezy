package dev.swirlit.indezy.mapper;

import dev.swirlit.indezy.dto.SeasonDto;
import dev.swirlit.indezy.model.Season;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface SeasonMapper {

    @Mapping(target = "freelanceId", source = "freelance.id")
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "projectCount", ignore = true)
    SeasonDto toDto(Season season);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "freelance", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Season toEntity(SeasonDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "freelance", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(SeasonDto dto, @MappingTarget Season season);
}
