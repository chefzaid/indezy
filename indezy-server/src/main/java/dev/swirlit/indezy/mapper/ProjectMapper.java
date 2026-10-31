package dev.swirlit.indezy.mapper;

import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.model.Project;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = InterviewStepMapper.class)
public interface ProjectMapper {

    @Mapping(target = "freelanceId", source = "freelance.id")
    @Mapping(target = "clientId", source = "client.id")
    @Mapping(target = "clientName", source = "client.companyName")
    @Mapping(target = "middlemanId", source = "middleman.id")
    @Mapping(target = "middlemanName", source = "middleman.companyName")
    @Mapping(target = "sourceId", source = "source.id")
    @Mapping(target = "sourceName", source = "source.name")
    @Mapping(target = "seasonId", source = "season.id")
    @Mapping(target = "seasonName", source = "season.name")
    @Mapping(target = "totalRevenue", expression = "java(project.getTotalRevenue())")
    @Mapping(target = "margin", expression = "java(project.getMargin())")
    @Mapping(target = "marginPercentage", expression = "java(project.getMarginPercentage())")
    @Mapping(target = "totalSteps", expression = "java(project.getSteps().size())")
    @Mapping(target = "completedSteps", expression = "java((int) project.getSteps().stream().filter(step -> dev.swirlit.indezy.model.enums.StepStatus.VALIDATED.equals(step.getStatus())).count())")
    @Mapping(target = "failedSteps", expression = "java((int) project.getSteps().stream().filter(step -> dev.swirlit.indezy.model.enums.StepStatus.FAILED.equals(step.getStatus())).count())")
    @Mapping(target = "steps", source = "steps", conditionExpression = "java(!project.getSteps().isEmpty())")
    ProjectDto toDto(Project project);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "freelance", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "middleman", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "season", ignore = true)
    @Mapping(target = "steps", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    @Mapping(target = "endedOn", ignore = true)
    @Mapping(target = "endReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Project toEntity(ProjectDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "freelance", ignore = true)
    @Mapping(target = "client", ignore = true)
    @Mapping(target = "middleman", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "season", ignore = true)
    @Mapping(target = "steps", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    @Mapping(target = "endedOn", ignore = true)
    @Mapping(target = "endReason", ignore = true)
    @Mapping(target = "documents", ignore = true)
    @Mapping(target = "contractChecks", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntity(ProjectDto dto, @MappingTarget Project project);

    /** Sets the documents whole: the entity hands out a copy, which a generated update would empty and fill in vain. */
    @AfterMapping
    default void replaceDocuments(ProjectDto dto, @MappingTarget Project project) {
        project.setDocuments(dto.getDocuments());
    }
}
