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
    private double forecastRevenue;
    private long activeProjects;
    private long wonProjects;
    private long lostProjects;
    private Map<String, Long> projectsByStatus;
    private Map<String, Long> projectsByWorkMode;
    private Map<String, Long> lostReasonsBreakdown;
    private List<DailyRateRange> dailyRateRanges;
    private List<SourceRoi> sourceRoi;
    private List<DailyRateEvolution> dailyRateEvolution;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRateRange {
        private String label;
        private long count;
    }

    /** Per-source ranking of how many opportunities turned into signed (WON) contracts. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceRoi {
        private String sourceName;
        private long totalProjects;
        private long wonProjects;
        private double conversionRate;
    }

    /** Average asked vs obtained daily rate per period (year), to show rate trends over time. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRateEvolution {
        private String period;
        private double averageAskedRate;
        private double averageObtainedRate;
        private long projectCount;
    }
}
