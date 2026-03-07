package dev.swirlit.indezy.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class KanbanBoardDto {
    private Map<String, List<ProjectCardDto>> columns;
    private List<String> columnOrder;
    
    @Data
    public static class ProjectCardDto {
        private Long projectId;
        private String role;
        private String status;
        private String clientName;
        private Integer dailyRate;
        private String workMode;
        private String techStack;
        private String sourceName;
        private String startDate;
        private Integer durationInMonths;
        private String notes;
        private Integer personalRating;
        private Integer totalSteps;
        private Integer completedSteps;
        private Integer failedSteps;
    }
}
