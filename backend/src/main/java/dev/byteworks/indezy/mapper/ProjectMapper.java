package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.ProjectDto;
import dev.byteworks.indezy.model.Project;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {InterviewStepMapper.class})
public interface ProjectMapper {

    @Mapping(target = "freelanceId", source = "freelance.id")
    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "clientName", source = "client.companyName")
    @Mapping(target = "middlemanId", source = "middleman.id")
    @Mapping(target = "middlemanName", source = "middleman.companyName")
    @Mapping(target = "sourceId", source = "source.id")
    @Mapping(target = "sourceName", source = "source.name")
    @Mapping(target = "totalRevenue", expression = "java(project.getTotalRevenue())")
    @Mapping(target = "totalSteps", expression = "java(project.getSteps().size())")
    @Mapping(target = "completedSteps", expression = "java((int) project.getSteps().stream().filter(step -> dev.byteworks.indezy.model.enums.StepStatus.VALIDATED.equals(step.getStatus())).count())")
    @Mapping(target = "failedSteps", expression = "java((int) project.getSteps().stream().filter(step -> dev.byteworks.indezy.model.enums.StepStatus.FAILED.equals(step.getStatus())).count())")
    @Mapping(target = "steps", source = "steps", conditionExpression = "java(!project.getSteps().isEmpty())")
    ProjectDto toDto(Project project);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "freelance", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "middleman", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "steps", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Project toEntity(ProjectDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "freelance", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "middleman", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "steps", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(ProjectDto dto, @MappingTarget Project project);
}
