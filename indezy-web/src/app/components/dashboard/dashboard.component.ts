import { Component, OnInit, ChangeDetectorRef, ViewChild, ElementRef, OnDestroy } from '@angular/core';
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
import { FreelanceService } from '../../services/freelance/freelance.service';
import { User, ProjectDto, FreelanceDto, DashboardStatsDto, PROJECT_STATUS_COLORS } from '../../models';
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
        KanbanBoardComponent
    ],
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit, OnDestroy {
  currentUser: User | null = null;
  freelanceProfile: FreelanceDto | null = null;
  recentProjects: ProjectDto[] = [];
  viewMode: ViewMode = 'overview';
  dashboardStats: DashboardStatsDto | null = null;

  showKanbanBoard = false;
  stats = {
    totalProjects: 0,
    averageDailyRate: 0,
    totalRevenue: 0,
    activeProjects: 0
  };

  @ViewChild('statusChart') statusChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('workModeChart') workModeChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('dailyRateChart') dailyRateChartRef!: ElementRef<HTMLCanvasElement>;

  private statusChart: Chart | null = null;
  private workModeChart: Chart | null = null;
  private dailyRateChart: Chart | null = null;
  constructor(
    private readonly authService: AuthService,
    private readonly projectService: ProjectService,
    private readonly freelanceService: FreelanceService,
    private readonly cdr: ChangeDetectorRef,
    private readonly translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.getUser();
    this.loadDashboardData();
  }

  ngOnDestroy(): void {
    this.destroyCharts();
  }

  loadDashboardData(): void {
    if (this.currentUser?.id) {
      this.freelanceService.getByIdWithProjects(this.currentUser.id).subscribe({
        next: (profile) => {
          this.freelanceProfile = profile;
          this.updateStats(profile);
        },
        error: () => {}
      });

      this.projectService.getByFreelanceId(this.currentUser.id).subscribe({
        next: (projects) => {
          this.recentProjects = projects.slice(0, 5);
        },
        error: () => {}
      });

      this.projectService.getDashboardStats(this.currentUser.id).subscribe({
        next: (stats) => {
          this.dashboardStats = stats;
          this.stats = {
            totalProjects: stats.totalProjects,
            averageDailyRate: stats.averageDailyRate,
            totalRevenue: stats.totalEstimatedRevenue,
            activeProjects: stats.activeProjects
          };
          this.cdr.detectChanges();
          this.buildCharts();
        },
        error: () => {}
      });
    }
  }

  updateStats(profile: FreelanceDto): void {
    this.stats = {
      totalProjects: profile.totalProjects || 0,
      averageDailyRate: profile.averageDailyRate || 0,
      totalRevenue: this.calculateTotalRevenue(),
      activeProjects: this.recentProjects.filter(p => p.startDate && new Date(p.startDate) <= new Date()).length
    };
  }

  calculateTotalRevenue(): number {
    return this.recentProjects.reduce((total, project) => {
      if (project.dailyRate && project.durationInMonths && project.daysPerYear) {
        const monthlyDays = project.daysPerYear / 12;
        return total + (project.dailyRate * monthlyDays * project.durationInMonths);
      }
      return total;
    }, 0);
  }

  logout(): void {
    this.authService.logout();
  }

  switchView(mode: any): void {
    if (mode === 'overview' || mode === 'kanban') {
      this.viewMode = mode as ViewMode;
      this.cdr.detectChanges();
      if (mode === 'overview' && this.dashboardStats) {
        setTimeout(() => this.buildCharts());
      }
    }
  }

  isOverviewMode(): boolean {
    return this.viewMode === 'overview';
  }

  isKanbanMode(): boolean {
    return this.viewMode === 'kanban';
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
    if (!this.dashboardStats) return;
    this.destroyCharts();
    this.buildStatusChart();
    this.buildWorkModeChart();
    this.buildDailyRateChart();
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
    if (!this.statusChartRef?.nativeElement || !this.dashboardStats) return;

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
          borderColor: '#fff'
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
    if (!this.workModeChartRef?.nativeElement || !this.dashboardStats) return;

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
          borderColor: '#fff'
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
    if (!this.dailyRateChartRef?.nativeElement || !this.dashboardStats) return;

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
