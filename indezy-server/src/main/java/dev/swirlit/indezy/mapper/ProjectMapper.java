package dev.swirlit.indezy.mapper;

import dev.swirlit.indezy.dto.InterviewStepDto;
import dev.swirlit.indezy.dto.ProjectDto;
import dev.swirlit.indezy.model.InterviewStep;
import dev.swirlit.indezy.model.Project;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
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
    @Mapping(target = "completedSteps", expression = "java((int) project.getSteps().stream().filter(step -> dev.swirlit.indezy.model.enums.StepStatus.VALIDATED.equals(step.getStatus())).count())")
    @Mapping(target = "failedSteps", expression = "java((int) project.getSteps().stream().filter(step -> dev.swirlit.indezy.model.enums.StepStatus.FAILED.equals(step.getStatus())).count())")
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

    default InterviewStepDto toInterviewStepDto(final InterviewStep interviewStep) {
        if (interviewStep == null) {
            return null;
        }

        InterviewStepDto dto = new InterviewStepDto();
        dto.setId(interviewStep.getId());
        dto.setTitle(interviewStep.getTitle());
        dto.setDate(interviewStep.getDate());
        dto.setStatus(interviewStep.getStatus());
        dto.setNotes(interviewStep.getNotes());

        Project project = interviewStep.getProject();
        if (project != null) {
            dto.setProjectId(project.getId());
            dto.setProjectRole(project.getRole());
        }

        return dto;
    }

    default List<InterviewStepDto> toInterviewStepDtos(final List<InterviewStep> interviewSteps) {
        if (interviewSteps == null) {
            return null;
        }

        return interviewSteps.stream()
            .map(this::toInterviewStepDto)
            .toList();
    }
}
