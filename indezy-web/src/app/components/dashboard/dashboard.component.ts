import { Component, OnInit, ChangeDetectorRef, ViewChild, ElementRef, OnDestroy, ChangeDetectionStrategy, NgZone, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth/auth.service';
import { ProjectService } from '../../services/project/project.service';
import { User, ProjectDto, DashboardStatsDto, PROJECT_STATUS_COLORS } from '../../models';
import { DashboardRemindersComponent } from './dashboard-reminders/dashboard-reminders.component';
import { DailyRateChartComponent } from './daily-rate-chart/daily-rate-chart.component';
import { DashboardOffersComponent } from './dashboard-offers/dashboard-offers.component';
import { DashboardAnalyticsComponent } from './dashboard-analytics/dashboard-analytics.component';
import { DashboardCustomizeDialogComponent, USUAL_LAYOUT } from './customize-dialog/dashboard-customize-dialog.component';
import { DashboardWidget, UserPreferences } from '../../models/user-management.models';
import { UserManagementService } from '../../services/user-management/user-management.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ThemeService } from '../../shared/theme/theme.service';
import { KanbanBoardComponent } from '../kanban-board/kanban-board.component';
import { SeasonPickerComponent } from '../seasons/season-picker/season-picker.component';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { Season } from '../../models/season.models';
import { forkJoin, of, Subscription } from 'rxjs';
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
        SeasonPickerComponent,
        PageHeaderComponent,
        DashboardRemindersComponent,
        DailyRateChartComponent,
        DashboardOffersComponent,
        DashboardAnalyticsComponent
    ],
    templateUrl: './dashboard.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit, OnDestroy {
  /** Remembers whether the user last looked at the overview or the pipeline board. */
  static readonly VIEW_MODE_KEY = 'indezy-dashboard-view';
  currentUser: User | null = null;
  recentProjects: ProjectDto[] = [];
  viewMode: ViewMode = 'overview';
  dashboardStats: DashboardStatsDto | null = null;

  /** Season whose dashboard is shown; null shows every opportunity. */
  selectedSeason: Season | null = null;
  /** Set once the season picker has resolved the initial season, so data loads only once. */
  seasonResolved = false;
  /** All-time statistics, loaded to compare a season against the global view. */
  globalStats: DashboardStatsDto | null = null;
  private allProjects: ProjectDto[] = [];
  private statsSubscription: Subscription | null = null;
  stats = {
    totalProjects: 0,
    averageDailyRate: 0,
    totalRevenue: 0,
    forecastRevenue: 0,
    activeProjects: 0
  };

  @ViewChild('statusChart') statusChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('workModeChart') workModeChartRef!: ElementRef<HTMLCanvasElement>;

  private statusChart: Chart | null = null;
  private workModeChart: Chart | null = null;
  private readonly users = inject(UserManagementService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);
  private readonly theme = inject(ThemeService);
  /** The overview's blocks in the account's order, shown or hidden. */
  widgets: DashboardWidget[] = USUAL_LAYOUT;
  private preferences: UserPreferences | null = null;
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
    this.users.getUserPreferences().subscribe({
      next: preferences => {
        this.preferences = preferences;
        this.widgets = preferences.dashboardWidgets?.length ? preferences.dashboardWidgets : USUAL_LAYOUT;
      },
      // The usual layout until the preferences can be read.
      error: () => this.preferences = null
    });
    if (!this.currentUser?.id) {
      // Nothing to pick a season for; keep the page usable.
      this.seasonResolved = true;
    }
  }

  /** Switches the dashboard (and its board) to another season, or to every season. */
  onSeasonChange(season: Season | null): void {
    this.selectedSeason = season;
    this.seasonResolved = true;
    this.loadDashboardData();
  }

  ngOnDestroy(): void {
    this.statsSubscription?.unsubscribe();
    this.destroyCharts();
  }

  loadDashboardData(): void {
    if (!this.currentUser?.id) {
      return;
    }
    const freelanceId = this.currentUser.id;
    const seasonId = this.selectedSeason?.id ?? null;

    this.projectService.getByFreelanceId(freelanceId).subscribe({
      next: (projects) => {
        this.allProjects = projects;
        this.updateRecentProjects();
      },
      error: (err) => console.error('Error loading recent projects:', err)
    });

    this.statsSubscription?.unsubscribe();
    this.statsSubscription = forkJoin({
      stats: this.projectService.getDashboardStats(freelanceId, seasonId),
      global: seasonId === null ? of(null) : this.projectService.getDashboardStats(freelanceId)
    }).subscribe({
      next: ({ stats, global }) => {
        this.dashboardStats = stats;
        this.globalStats = global;
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

  /** Newest opportunities of the selected season (ids grow with creation order). */
  private updateRecentProjects(): void {
    const seasonId = this.selectedSeason?.id;
    this.recentProjects = this.allProjects
      .filter(project => seasonId === undefined || project.seasonId === seasonId)
      .sort((a, b) => (b.id ?? 0) - (a.id ?? 0))
      .slice(0, 6);
  }

  /** Share of decided opportunities that were accepted, in percent (null before any decision). */
  static successRate(stats: DashboardStatsDto | null): number | null {
    if (!stats) {
      return null;
    }
    const decided = stats.wonProjects + stats.lostProjects;
    return decided > 0 ? Math.round((stats.wonProjects / decided) * 100) : null;
  }

  seasonSuccessRate(): number | null {
    return DashboardComponent.successRate(this.dashboardStats);
  }

  globalSuccessRate(): number | null {
    return DashboardComponent.successRate(this.globalStats);
  }

  /** Days elapsed since the season started (inclusive), capped at its end date. */
  seasonDays(season: Season): number {
    const start = new Date(season.startDate + 'T00:00:00');
    const end = season.endDate ? new Date(season.endDate + 'T00:00:00') : new Date();
    const last = end.getTime() < Date.now() ? end : new Date();
    return Math.max(1, Math.floor((last.getTime() - start.getTime()) / 86_400_000) + 1);
  }

  /** Average daily rate of the season compared with its target, in percent of the target. */
  targetProgress(season: Season): number | null {
    if (!season.targetDailyRate || !this.stats.averageDailyRate) {
      return null;
    }
    return Math.round((this.stats.averageDailyRate / season.targetDailyRate) * 100);
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

  get visibleWidgets(): DashboardWidget[] {
    return this.widgets.filter(widget => widget.visible);
  }

  /** Lets the account arrange the overview's blocks, then saves the layout with its other preferences. */
  customize(): void {
    this.dialog.open(DashboardCustomizeDialogComponent, { data: this.widgets, width: '480px', autoFocus: false })
      .afterClosed().subscribe((arranged: DashboardWidget[] | undefined) => {
        if (!arranged || !this.preferences) {
          return;
        }
        const before = this.widgets;
        this.widgets = arranged;
        this.users.updateUserPreferences({ ...this.preferences, dashboardWidgets: arranged }).subscribe({
          next: saved => {
            this.preferences = saved;
            this.notifications.success('dashboard.widgets.saved');
          },
          error: () => {
            this.widgets = before;
            this.notifications.error('dashboard.widgets.saveError');
          }
        });
        // The charts' canvases may have moved or come back.
        this.cdr.detectChanges();
        setTimeout(() => this.buildCharts());
      });
  }

  isOverviewMode(): boolean {
    return this.viewMode === 'overview';
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

  /** The page's title: the greeting of the hour, with the user's first name when it is known. */
  greeting(): string {
    const firstName = this.currentUser?.firstName;
    return firstName ? `${this.getGreeting()}, ${firstName}` : this.getGreeting();
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
    });
  }

  /**
   * A stage colour as a slice on the card. The stage colours carry white text, so they are dark: on the dark theme's
   * card they are lifted toward white (60 % of the colour), as the board's columns are.
   */
  private onSurface(color: string): string {
    if (!this.theme.isDark() || !/^#[0-9a-f]{6}$/i.test(color)) {return color;}
    return '#' + (color.slice(1).match(/../g) ?? [])
      .map(channel => Math.round(parseInt(channel, 16) * 0.6 + 255 * 0.4).toString(16).padStart(2, '0'))
      .join('');
  }

  private destroyCharts(): void {
    this.statusChart?.destroy();
    this.workModeChart?.destroy();
    this.statusChart = null;
    this.workModeChart = null;
  }

  private buildStatusChart(): void {
    if (!this.statusChartRef?.nativeElement || !this.dashboardStats) {return;}

    const statusKeys = Object.keys(this.dashboardStats.projectsByStatus);
    const statusLabels = statusKeys.map(s => this.translate.instant('projects.statuses.' + s));
    const statusData = statusKeys.map(s => this.dashboardStats!.projectsByStatus[s]);
    const statusColors = statusKeys.map(s => this.onSurface(PROJECT_STATUS_COLORS[s as keyof typeof PROJECT_STATUS_COLORS] || '#999'));

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

}
