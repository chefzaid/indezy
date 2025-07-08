import { Component, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../../services/auth/auth.service';
import { ProjectService } from '../../services/project/project.service';
import { FreelanceService } from '../../services/freelance/freelance.service';
import { User, ProjectDto, FreelanceDto } from '../../models';
import { KanbanBoardComponent } from '../kanban-board/kanban-board.component';

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
        KanbanBoardComponent
    ],
    templateUrl: './dashboard.component.html',
    styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  currentUser: User | null = null;
  freelanceProfile: FreelanceDto | null = null;
  recentProjects: ProjectDto[] = [];
  viewMode: ViewMode = 'overview';

  showKanbanBoard = false; // Control kanban board visibility
  stats = {
    totalProjects: 0,
    averageDailyRate: 0,
    totalRevenue: 0,
    activeProjects: 0
  };



  constructor(
    private readonly authService: AuthService,
    private readonly projectService: ProjectService,
    private readonly freelanceService: FreelanceService,
    private readonly cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.getUser();
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    if (this.currentUser?.id) {
      // Load freelance profile
      this.freelanceService.getByIdWithProjects(this.currentUser.id).subscribe({
        next: (profile) => {
          this.freelanceProfile = profile;
          this.updateStats(profile);
        },
        error: (error) => {
          // Handle error silently or show user-friendly message
        }
      });

      // Load recent projects
      this.projectService.getByFreelanceId(this.currentUser.id).subscribe({
        next: (projects) => {
          this.recentProjects = projects.slice(0, 5); // Get 5 most recent
        },
        error: (error) => {
          // Handle error silently or show user-friendly message
        }
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
      return 'Bonjour';
    } else if (hour < 18) {
      return 'Bon après-midi';
    } else {
      return 'Bonsoir';
    }
  }
}
