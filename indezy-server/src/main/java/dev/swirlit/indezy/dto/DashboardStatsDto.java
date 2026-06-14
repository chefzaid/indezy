package dev.swirlit.indezy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDto {
    private long totalProjects;
    private double averageDailyRate;
    private double totalEstimatedRevenue;
    private long activeProjects;
    private long wonProjects;
    private long lostProjects;
    private Map<String, Long> projectsByStatus;
    private Map<String, Long> projectsByWorkMode;
    private List<DailyRateRange> dailyRateRanges;
    // Breakdown of lost opportunities by reason (reason name -> count), including UNSPECIFIED.
    private Map<String, Long> lostReasonsBreakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRateRange {
        private String label;
        private long count;
    }
}
