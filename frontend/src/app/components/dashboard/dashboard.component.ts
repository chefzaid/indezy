import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { AuthService } from '../../services/auth.service';
import { ProjectService, ProjectDto } from '../../services/project.service';
import { FreelanceService, FreelanceDto } from '../../services/freelance.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatIconModule,
    MatButtonModule,
    MatCardModule
  ],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  currentUser: any;
  freelanceProfile: FreelanceDto | null = null;
  recentProjects: ProjectDto[] = [];
  stats = {
    totalProjects: 0,
    averageDailyRate: 0,
    totalRevenue: 0,
    activeProjects: 0
  };



  constructor(
    private authService: AuthService,
    private projectService: ProjectService,
    private freelanceService: FreelanceService
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
        error: (error) => console.error('Error loading profile:', error)
      });

      // Load recent projects
      this.projectService.getByFreelanceId(this.currentUser.id).subscribe({
        next: (projects) => {
          this.recentProjects = projects.slice(0, 5); // Get 5 most recent
        },
        error: (error) => console.error('Error loading projects:', error)
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

  getGreeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Bonjour';
    if (hour < 18) return 'Bon après-midi';
    return 'Bonsoir';
  }
}
