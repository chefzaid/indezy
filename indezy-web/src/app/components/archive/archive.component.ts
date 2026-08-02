import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { TranslateModule } from '@ngx-translate/core';

import { ProjectService } from '../../services/project/project.service';
import { AuthService } from '../../services/auth/auth.service';
import { ProjectDto, ProjectStatus } from '../../models';
import { LoadingComponent } from '../../shared/components/loading/loading.component';

/**
 * Read-only archive of closed opportunities (won or lost) with a full-text search
 * across their role, description, tech stack, notes and client.
 */
@Component({
  selector: 'app-archive',
  imports: [
    CommonModule, FormsModule, RouterModule, MatCardModule, MatIconModule,
    MatFormFieldModule, MatInputModule, MatChipsModule, TranslateModule, LoadingComponent
  ],
  templateUrl: './archive.component.html',
  styleUrls: ['./archive.component.scss']
})
export class ArchiveComponent implements OnInit {
  filteredProjects: ProjectDto[] = [];
  searchQuery = '';
  isLoading = false;

  private archivedProjects: ProjectDto[] = [];

  constructor(
    private readonly projectService: ProjectService,
    private readonly authService: AuthService
  ) {}

  ngOnInit(): void {
    const freelanceId = this.authService.getUser()?.id;
    if (!freelanceId) {
      return;
    }
    this.isLoading = true;
    this.projectService.getByFreelanceId(freelanceId).subscribe({
      next: projects => {
        this.archivedProjects = this.toArchived(projects);
        this.applySearch();
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
      }
    });
  }

  onSearch(): void {
    this.applySearch();
  }

  /** Keeps only closed (won/lost) opportunities, most recent first. */
  toArchived(projects: ProjectDto[]): ProjectDto[] {
    const closed: ProjectStatus[] = [ProjectStatus.WON, ProjectStatus.LOST];
    return projects
      .filter(project => !!project.status && closed.includes(project.status))
      .sort((a, b) => this.startTime(b) - this.startTime(a));
  }

  /** Filters projects whose searchable text fields contain the query (case-insensitive). */
  matchQuery(projects: ProjectDto[], query: string): ProjectDto[] {
    const normalized = query.trim().toLowerCase();
    if (!normalized) {
      return projects;
    }
    return projects.filter(project =>
      [project.role, project.description, project.techStack, project.notes, project.clientName]
        .some(field => (field ?? '').toLowerCase().includes(normalized)));
  }

  private applySearch(): void {
    this.filteredProjects = this.matchQuery(this.archivedProjects, this.searchQuery);
  }

  private startTime(project: ProjectDto): number {
    return project.startDate ? new Date(project.startDate).getTime() : 0;
  }
}
