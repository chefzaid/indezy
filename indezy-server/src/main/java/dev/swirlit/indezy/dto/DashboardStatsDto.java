package dev.swirlit.indezy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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
    private long totalBenchDays;
    private long benchPeriods;
    private double estimatedBenchCost;
    private Map<String, Long> projectsByStatus;
    private Map<String, Long> projectsByWorkMode;
    private Map<String, Long> lostReasonsBreakdown;
    private List<DailyRateRange> dailyRateRanges;
    private List<SourceRoi> sourceRoi;
    private List<DailyRateEvolution> dailyRateEvolution;
    private List<ConversionFunnelStage> conversionFunnel;
    private List<FunnelBreakdown> funnelBySource;
    private List<FunnelBreakdown> funnelByClientType;
    private List<FunnelBreakdown> funnelByEsn;
    private List<MissionEndingSoon> missionsEndingSoon;

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

    /**
     * One stage of the pipeline conversion funnel. {@code count} is how many opportunities
     * reached at least this stage; {@code conversionRate} is that count as a percentage of the
     * first stage, so the drop between stages shows where opportunities die.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversionFunnelStage {
        private String stage;
        private long count;
        private double conversionRate;
    }

    /**
     * A conversion funnel for one slice of the pipeline (a single source, client type or ESN),
     * so drop-off can be compared across groups to see where opportunities die per dimension.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunnelBreakdown {
        private String group;
        private List<ConversionFunnelStage> stages;
    }

    /**
     * A signed mission whose end date is approaching, so prospection can be restarted before the
     * bench. {@code daysUntilEnd} is the number of days from today to {@code endDate}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissionEndingSoon {
        private Long projectId;
        private String role;
        private String clientName;
        private LocalDate endDate;
        private long daysUntilEnd;
    }
}
