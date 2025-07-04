package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.ProjectDto;
import dev.byteworks.indezy.model.Project;
import dev.byteworks.indezy.model.enums.StepStatus;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public ProjectDto toDto(Project project) {
        if (project == null) {
            return null;
        }

        ProjectDto dto = new ProjectDto();
        dto.setId(project.getId());
        dto.setRole(project.getRole());
        dto.setDescription(project.getDescription());
        dto.setTechStack(project.getTechStack());
        dto.setDailyRate(project.getDailyRate());
        dto.setWorkMode(project.getWorkMode());
        dto.setRemoteDaysPerMonth(project.getRemoteDaysPerMonth());
        dto.setOnsiteDaysPerMonth(project.getOnsiteDaysPerMonth());
        dto.setAdvantages(project.getAdvantages());
        dto.setStartDate(project.getStartDate());
        dto.setDurationInMonths(project.getDurationInMonths());
        dto.setOrderRenewalInMonths(project.getOrderRenewalInMonths());
        dto.setDaysPerYear(project.getDaysPerYear());
        dto.setDocuments(project.getDocuments());
        dto.setLink(project.getLink());
        dto.setPersonalRating(project.getPersonalRating());
        dto.setNotes(project.getNotes());

        // Related entities
        if (project.getFreelance() != null) {
            dto.setFreelanceId(project.getFreelance().getId());
        }
        if (project.getClient() != null) {
            dto.setClientId(project.getClient().getId());
            dto.setClientName(project.getClient().getCompanyName());
        }
        if (project.getMiddleman() != null) {
            dto.setMiddlemanId(project.getMiddleman().getId());
            dto.setMiddlemanName(project.getMiddleman().getCompanyName());
        }
        if (project.getSource() != null) {
            dto.setSourceId(project.getSource().getId());
            dto.setSourceName(project.getSource().getName());
        }

        // Computed fields
        dto.setTotalRevenue(project.getTotalRevenue());
        if (project.getSteps() != null) {
            dto.setTotalSteps(project.getSteps().size());
            dto.setCompletedSteps((int) project.getSteps().stream()
                .filter(step -> StepStatus.VALIDATED.equals(step.getStatus()))
                .count());
            dto.setFailedSteps((int) project.getSteps().stream()
                .filter(step -> StepStatus.FAILED.equals(step.getStatus()))
                .count());
        }

        return dto;
    }

    public Project toEntity(ProjectDto dto) {
        if (dto == null) {
            return null;
        }

        Project project = new Project();
        project.setId(dto.getId());
        project.setRole(dto.getRole());
        project.setDescription(dto.getDescription());
        project.setTechStack(dto.getTechStack());
        project.setDailyRate(dto.getDailyRate());
        project.setWorkMode(dto.getWorkMode());
        project.setRemoteDaysPerMonth(dto.getRemoteDaysPerMonth());
        project.setOnsiteDaysPerMonth(dto.getOnsiteDaysPerMonth());
        project.setAdvantages(dto.getAdvantages());
        project.setStartDate(dto.getStartDate());
        project.setDurationInMonths(dto.getDurationInMonths());
        project.setOrderRenewalInMonths(dto.getOrderRenewalInMonths());
        project.setDaysPerYear(dto.getDaysPerYear());
        if (dto.getDocuments() != null) {
            project.setDocuments(dto.getDocuments());
        }
        project.setLink(dto.getLink());
        project.setPersonalRating(dto.getPersonalRating());
        project.setNotes(dto.getNotes());

        return project;
    }

    public void updateEntity(ProjectDto dto, Project project) {
        if (dto == null || project == null) {
            return;
        }

        project.setRole(dto.getRole());
        project.setDescription(dto.getDescription());
        project.setTechStack(dto.getTechStack());
        project.setDailyRate(dto.getDailyRate());
        project.setWorkMode(dto.getWorkMode());
        project.setRemoteDaysPerMonth(dto.getRemoteDaysPerMonth());
        project.setOnsiteDaysPerMonth(dto.getOnsiteDaysPerMonth());
        project.setAdvantages(dto.getAdvantages());
        project.setStartDate(dto.getStartDate());
        project.setDurationInMonths(dto.getDurationInMonths());
        project.setOrderRenewalInMonths(dto.getOrderRenewalInMonths());
        project.setDaysPerYear(dto.getDaysPerYear());
        if (dto.getDocuments() != null) {
            project.setDocuments(dto.getDocuments());
        }
        project.setLink(dto.getLink());
        project.setPersonalRating(dto.getPersonalRating());
        project.setNotes(dto.getNotes());
    }
}
