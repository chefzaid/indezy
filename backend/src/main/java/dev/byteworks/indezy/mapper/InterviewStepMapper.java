package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.InterviewStepDto;
import dev.byteworks.indezy.model.InterviewStep;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface InterviewStepMapper {

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectRole", source = "project.role")
    InterviewStepDto toDto(InterviewStep interviewStep);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    InterviewStep toEntity(InterviewStepDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(InterviewStepDto dto, @MappingTarget InterviewStep interviewStep);
}
