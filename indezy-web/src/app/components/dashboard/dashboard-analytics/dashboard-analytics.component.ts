import { Component, Input, OnChanges, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import {
  DashboardStatsDto, SourceRoi, DailyRateEvolution, ConversionFunnelStage, FunnelBreakdown, SkillTrend, ProcessDuration, ActivityDay, Trend
} from '../../../models';
import { ActivityHeatmapComponent } from '../../../shared/components/activity-heatmap/activity-heatmap.component';
import { BilledDaysComponent } from '../billed-days/billed-days.component';
import { CommuteStatsComponent } from '../commute-stats/commute-stats.component';
import { RateTargetComponent } from '../rate-target/rate-target.component';

/**
 * Renders the dashboard's analytics (rate target, outcomes and lost reasons, conversion funnels, source ROI, skill
 * demand, rate evolution, process durations, bench time, activity heatmap) from the aggregated dashboard stats.
 */
@Component({
  selector: 'app-dashboard-analytics',
  imports: [CommonModule, MatIconModule, TranslateModule, ActivityHeatmapComponent, RateTargetComponent, BilledDaysComponent, CommuteStatsComponent],
  templateUrl: './dashboard-analytics.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./dashboard-analytics.component.scss']
})
export class DashboardAnalyticsComponent implements OnChanges {
  static readonly TOP_SKILLS = 8;
  @Input() stats: DashboardStatsDto | null = null;

  get trends(): Trend[] {
    return this.stats?.trends ?? [];
  }
  /** Lost reasons that actually occurred, sorted by frequency (computed once per stats change). */
  lostReasons: { reason: string; count: number }[] = [];

  ngOnChanges(): void {
    this.lostReasons = DashboardAnalyticsComponent.buildLostReasons(this.stats);
  }

  private static buildLostReasons(stats: DashboardStatsDto | null): { reason: string; count: number }[] {
    return Object.entries(stats?.lostReasonsBreakdown ?? {})
      .filter(([, count]) => count > 0)
      .map(([reason, count]) => ({ reason, count }))
      .sort((a, b) => b.count - a.count);
  }

  /** Per-source ROI ranking, already sorted by signed contracts from the backend. */
  getSourceRoi(): SourceRoi[] {
    return this.stats?.sourceRoi ?? [];
  }

  /** Asked vs obtained daily rate per year, already ordered chronologically by the backend. */
  getDailyRateEvolution(): DailyRateEvolution[] {
    return this.stats?.dailyRateEvolution ?? [];
  }

  /** Pipeline conversion funnel stages, already ordered from first to last stage by the backend. */
  getConversionFunnel(): ConversionFunnelStage[] {
    return this.stats?.conversionFunnel ?? [];
  }

  /** Per-source conversion funnels, ordered by source name by the backend. */
  getFunnelBySource(): FunnelBreakdown[] {
    return this.stats?.funnelBySource ?? [];
  }

  /** Conversion funnels split by direct vs intermediated (ESN) deals. */
  getFunnelByClientType(): FunnelBreakdown[] {
    return this.stats?.funnelByClientType ?? [];
  }

  /** Per-ESN conversion funnels, ordered by ESN name by the backend. */
  getFunnelByEsn(): FunnelBreakdown[] {
    return this.stats?.funnelByEsn ?? [];
  }

  getSkillTrends(): SkillTrend[] {
    return this.stats?.skillTrends ?? [];
  }

  /** The most requested skills (the backend already sorts them by demand). */
  getTopSkillTrends(): SkillTrend[] {
    return this.getSkillTrends().slice(0, DashboardAnalyticsComponent.TOP_SKILLS);
  }

  getProcessDurations(): ProcessDuration[] {
    return this.stats?.processDurations ?? [];
  }

  getActivityHeatmap(): ActivityDay[] {
    return this.stats?.activityHeatmap ?? [];
  }

  /** Compact stage counts for a funnel group, e.g. "6 → 4 → 3 → 2 → 1". */
  funnelCounts(group: FunnelBreakdown): string {
    return group.stages.map(s => s.count).join(' → ');
  }

  /** Overall conversion rate of a funnel group (its last stage relative to the first). */
  funnelConversion(group: FunnelBreakdown): number {
    return group.stages.at(-1)?.conversionRate ?? 0;
  }
}
