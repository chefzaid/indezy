package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.InterviewStepDto;
import dev.byteworks.indezy.model.InterviewStep;
import org.springframework.stereotype.Component;

@Component
public class InterviewStepMapper {

    public InterviewStepDto toDto(InterviewStep interviewStep) {
        if (interviewStep == null) {
            return null;
        }

        InterviewStepDto dto = new InterviewStepDto();
        dto.setId(interviewStep.getId());
        dto.setTitle(interviewStep.getTitle());
        dto.setDate(interviewStep.getDate());
        dto.setStatus(interviewStep.getStatus());
        dto.setNotes(interviewStep.getNotes());

        // Related entities
        if (interviewStep.getProject() != null) {
            dto.setProjectId(interviewStep.getProject().getId());
            dto.setProjectRole(interviewStep.getProject().getRole());
        }

        return dto;
    }

    public InterviewStep toEntity(InterviewStepDto dto) {
        if (dto == null) {
            return null;
        }

        InterviewStep interviewStep = new InterviewStep();
        interviewStep.setId(dto.getId());
        interviewStep.setTitle(dto.getTitle());
        interviewStep.setDate(dto.getDate());
        interviewStep.setStatus(dto.getStatus());
        interviewStep.setNotes(dto.getNotes());

        return interviewStep;
    }

    public void updateEntity(InterviewStepDto dto, InterviewStep interviewStep) {
        if (dto == null || interviewStep == null) {
            return;
        }

        interviewStep.setTitle(dto.getTitle());
        interviewStep.setDate(dto.getDate());
        interviewStep.setStatus(dto.getStatus());
        interviewStep.setNotes(dto.getNotes());
    }
}
