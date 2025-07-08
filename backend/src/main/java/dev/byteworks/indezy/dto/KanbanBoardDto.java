package dev.byteworks.indezy.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class KanbanBoardDto {
    private Map<String, List<ProjectCardDto>> columns;
    private List<String> stepOrder;
    
    @Data
    public static class ProjectCardDto {
        private Long projectId;
        private String role;
        private String clientName;
        private Integer dailyRate;
        private String workMode;
        private String techStack;
        private String currentStepTitle;
        private String currentStepStatus;
        private String currentStepDate;
        private String notes;
        private Integer totalSteps;
        private Integer completedSteps;
        private Integer failedSteps;
    }
}
