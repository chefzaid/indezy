import { Component, OnInit, ChangeDetectorRef, ViewChild, ElementRef, OnDestroy, ChangeDetectionStrategy, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth/auth.service';
import { ProjectService } from '../../services/project/project.service';
import { User, ProjectDto, DashboardStatsDto, SourceRoi, DailyRateEvolution, ConversionFunnelStage, FunnelBreakdown, SkillTrend, ProcessDuration, ActivityDay, PROJECT_STATUS_COLORS } from '../../models';
import { ActivityHeatmapComponent } from '../../shared/components/activity-heatmap/activity-heatmap.component';
import { DashboardRemindersComponent } from './dashboard-reminders/dashboard-reminders.component';
import { KanbanBoardComponent } from '../kanban-board/kanban-board.component';
import { Chart, ChartConfiguration, registerables } from 'chart.js';

Chart.register(...registerables);

type ViewMode = 'overview' | 'kanban';

@Component({
    selector: 'app-dashboard',
    imports: [
        CommonModule,
        RouterModule,
        MatIconModule,
        MatButtonModule,
        MatCardModule,
        MatButtonToggleModule,
        MatTooltipModule,
        TranslateModule,
        KanbanBoardComponent,
        ActivityHeatmapComponent,
        DashboardRemindersComponent
    ],
    templateUrl: './dashboard.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit, OnDestroy {
  /** Remembers whether the user last looked at the overview or the pipeline board. */
  static readonly VIEW_MODE_KEY = 'indezy-dashboard-view';
  static readonly TOP_SKILLS = 8;
  currentUser: User | null = null;
  recentProjects: ProjectDto[] = [];
  viewMode: ViewMode = 'overview';
  dashboardStats: DashboardStatsDto | null = null;
  /** Lost reasons that actually occurred, sorted by frequency (computed once per stats load). */
  lostReasons: { reason: string; count: number }[] = [];

  showKanbanBoard = false;
  stats = {
    totalProjects: 0,
    averageDailyRate: 0,
    totalRevenue: 0,
    forecastRevenue: 0,
    activeProjects: 0
  };

  @ViewChild('statusChart') statusChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('workModeChart') workModeChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('dailyRateChart') dailyRateChartRef!: ElementRef<HTMLCanvasElement>;

  private statusChart: Chart | null = null;
  private workModeChart: Chart | null = null;
  private dailyRateChart: Chart | null = null;
  private chartSurface = '#fff';
  constructor(
    private readonly authService: AuthService,
    private readonly projectService: ProjectService,
    private readonly cdr: ChangeDetectorRef,
    private readonly translate: TranslateService,
    private readonly ngZone: NgZone
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.getUser();
    this.viewMode = DashboardComponent.readSavedViewMode();
    this.loadDashboardData();
  }

  ngOnDestroy(): void {
    this.destroyCharts();
  }

  loadDashboardData(): void {
    if (this.currentUser?.id) {
      this.projectService.getByFreelanceId(this.currentUser.id).subscribe({
        next: (projects) => {
          // Newest opportunities first (ids grow with creation order).
          this.recentProjects = [...projects].sort((a, b) => (b.id ?? 0) - (a.id ?? 0)).slice(0, 6);
        },
        error: (err) => console.error('Error loading recent projects:', err)
      });

      this.projectService.getDashboardStats(this.currentUser.id).subscribe({
        next: (stats) => {
          this.dashboardStats = stats;
          this.lostReasons = this.buildLostReasons(stats);
          this.stats = {
            totalProjects: stats.totalProjects,
            averageDailyRate: stats.averageDailyRate,
            totalRevenue: stats.totalEstimatedRevenue,
            forecastRevenue: stats.forecastRevenue,
            activeProjects: stats.activeProjects
          };
          this.cdr.detectChanges();
          this.buildCharts();
        },
        error: (err) => console.error('Error loading dashboard stats:', err)
      });
    }
  }

  logout(): void {
    this.authService.logout();
  }

  switchView(mode: string): void {
    if (mode === 'overview' || mode === 'kanban') {
      this.viewMode = mode;
      try {
        localStorage.setItem(DashboardComponent.VIEW_MODE_KEY, mode);
      } catch {
        // Storage can be unavailable (private mode); the choice then lasts for this visit only.
      }
      this.cdr.detectChanges();
      if (mode === 'overview' && this.dashboardStats) {
        setTimeout(() => this.buildCharts());
      }
    }
  }

  private static readSavedViewMode(): ViewMode {
    try {
      return localStorage.getItem(DashboardComponent.VIEW_MODE_KEY) === 'kanban' ? 'kanban' : 'overview';
    } catch {
      return 'overview';
    }
  }

  isOverviewMode(): boolean {
    return this.viewMode === 'overview';
  }

  isKanbanMode(): boolean {
    return this.viewMode === 'kanban';
  }

  private buildLostReasons(stats: DashboardStatsDto): { reason: string; count: number }[] {
    return Object.entries(stats.lostReasonsBreakdown ?? {})
      .filter(([, count]) => count > 0)
      .map(([reason, count]) => ({ reason, count }))
      .sort((a, b) => b.count - a.count);
  }

  /** Per-source ROI ranking, already sorted by signed contracts from the backend. */
  getSourceRoi(): SourceRoi[] {
    return this.dashboardStats?.sourceRoi ?? [];
  }

  /** Asked vs obtained daily rate per year, already ordered chronologically by the backend. */
  getDailyRateEvolution(): DailyRateEvolution[] {
    return this.dashboardStats?.dailyRateEvolution ?? [];
  }

  /** Pipeline conversion funnel stages, already ordered from first to last stage by the backend. */
  getConversionFunnel(): ConversionFunnelStage[] {
    return this.dashboardStats?.conversionFunnel ?? [];
  }

  /** Per-source conversion funnels, ordered by source name by the backend. */
  getFunnelBySource(): FunnelBreakdown[] {
    return this.dashboardStats?.funnelBySource ?? [];
  }

  /** Conversion funnels split by direct vs intermediated (ESN) deals. */
  getFunnelByClientType(): FunnelBreakdown[] {
    return this.dashboardStats?.funnelByClientType ?? [];
  }

  /** Per-ESN conversion funnels, ordered by ESN name by the backend. */
  getFunnelByEsn(): FunnelBreakdown[] {
    return this.dashboardStats?.funnelByEsn ?? [];
  }

  getSkillTrends(): SkillTrend[] {
    return this.dashboardStats?.skillTrends ?? [];
  }

  /** The most requested skills (the backend already sorts them by demand). */
  getTopSkillTrends(): SkillTrend[] {
    return this.getSkillTrends().slice(0, DashboardComponent.TOP_SKILLS);
  }

  getProcessDurations(): ProcessDuration[] {
    return this.dashboardStats?.processDurations ?? [];
  }

  getActivityHeatmap(): ActivityDay[] {
    return this.dashboardStats?.activityHeatmap ?? [];
  }

  /** Compact stage counts for a funnel group, e.g. "6 → 4 → 3 → 2 → 1". */
  funnelCounts(group: FunnelBreakdown): string {
    return group.stages.map(s => s.count).join(' → ');
  }

  /** Overall conversion rate of a funnel group (its last stage relative to the first). */
  funnelConversion(group: FunnelBreakdown): number {
    return group.stages.at(-1)?.conversionRate ?? 0;
  }

  setKanbanMode(): void {
    this.viewMode = 'kanban';
    this.showKanbanBoard = true;
    this.cdr.detectChanges();
  }

  setOverviewMode(): void {
    this.viewMode = 'overview';
    this.showKanbanBoard = false;
    this.cdr.detectChanges();
  }

  getGreeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) {
      return this.translate.instant('dashboard.greetingMorning');
    } else if (hour < 18) {
      return this.translate.instant('dashboard.greetingAfternoon');
    } else {
      return this.translate.instant('dashboard.greetingEvening');
    }
  }

  private buildCharts(): void {
    if (!this.dashboardStats) {return;}
    this.destroyCharts();
    // Chart.js animates and observes resizes through requestAnimationFrame and
    // ResizeObserver; keep those callbacks from triggering application-wide
    // change detection on every frame.
    // Follow the light/dark theme: axis and legend text plus slice separators use app tokens.
    const styles = getComputedStyle(document.documentElement);
    Chart.defaults.color = styles.getPropertyValue('--app-text-muted').trim() || '#666';
    Chart.defaults.borderColor = styles.getPropertyValue('--app-border').trim() || 'rgba(0, 0, 0, 0.1)';
    this.chartSurface = styles.getPropertyValue('--app-surface').trim() || '#fff';
    this.ngZone.runOutsideAngular(() => {
      this.buildStatusChart();
      this.buildWorkModeChart();
      this.buildDailyRateChart();
    });
  }

  private destroyCharts(): void {
    this.statusChart?.destroy();
    this.workModeChart?.destroy();
    this.dailyRateChart?.destroy();
    this.statusChart = null;
    this.workModeChart = null;
    this.dailyRateChart = null;
  }

  private buildStatusChart(): void {
    if (!this.statusChartRef?.nativeElement || !this.dashboardStats) {return;}

    const statusKeys = Object.keys(this.dashboardStats.projectsByStatus);
    const statusLabels = statusKeys.map(s => this.translate.instant('projects.statuses.' + s));
    const statusData = statusKeys.map(s => this.dashboardStats!.projectsByStatus[s]);
    const statusColors = statusKeys.map(s => PROJECT_STATUS_COLORS[s as keyof typeof PROJECT_STATUS_COLORS] || '#999');

    const config: ChartConfiguration<'doughnut'> = {
      type: 'doughnut',
      data: {
        labels: statusLabels,
        datasets: [{
          data: statusData,
          backgroundColor: statusColors,
          borderWidth: 2,
          borderColor: this.chartSurface
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: { padding: 16, usePointStyle: true, pointStyle: 'circle' }
          }
        }
      }
    };

    this.statusChart = new Chart(this.statusChartRef.nativeElement, config);
  }

  private buildWorkModeChart(): void {
    if (!this.workModeChartRef?.nativeElement || !this.dashboardStats) {return;}

    const modeKeys = Object.keys(this.dashboardStats.projectsByWorkMode);
    const modeLabels = modeKeys.map(m => this.translate.instant('projects.workModes.' + m));
    const modeData = modeKeys.map(m => this.dashboardStats!.projectsByWorkMode[m]);
    const modeColors = ['#4facfe', '#43e97b', '#f5576c'];

    const config: ChartConfiguration<'doughnut'> = {
      type: 'doughnut',
      data: {
        labels: modeLabels,
        datasets: [{
          data: modeData,
          backgroundColor: modeColors,
          borderWidth: 2,
          borderColor: this.chartSurface
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: { padding: 16, usePointStyle: true, pointStyle: 'circle' }
          }
        }
      }
    };

    this.workModeChart = new Chart(this.workModeChartRef.nativeElement, config);
  }

  private buildDailyRateChart(): void {
    if (!this.dailyRateChartRef?.nativeElement || !this.dashboardStats) {return;}

    const ranges = this.dashboardStats.dailyRateRanges;
    const labels = ranges.map(r => r.label + '€');
    const data = ranges.map(r => r.count);

    const config: ChartConfiguration<'bar'> = {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: this.translate.instant('dashboard.charts.projects'),
          data,
          backgroundColor: 'rgba(102, 126, 234, 0.7)',
          borderColor: '#667eea',
          borderWidth: 1,
          borderRadius: 6
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        scales: {
          y: {
            beginAtZero: true,
            ticks: { stepSize: 1 }
          }
        },
        plugins: {
          legend: { display: false }
        }
      }
    };

    this.dailyRateChart = new Chart(this.dailyRateChartRef.nativeElement, config);
  }
}
