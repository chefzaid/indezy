import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';

import { ProjectService } from '../../services/project/project.service';
import { AuthService } from '../../services/auth/auth.service';
import { ProjectDto, ProjectStatus } from '../../models';
import { LoadingComponent } from '../../shared/components/loading/loading.component';
import { NotificationService } from '../../services/notification/notification.service';
import { EnterOpensDirective } from '../../shared/directives/enter-opens.directive';

/**
 * Archive of closed opportunities (won or lost) and of the ones taken out of the pipeline, by hand or after the
 * inactivity period, with a full-text search across their role, description, tech stack, notes and client. An archived
 * opportunity can be restored to its pipeline column.
 */
@Component({
  selector: 'app-archive',
  imports: [
    CommonModule, FormsModule, RouterModule, MatCardModule, MatIconModule, EnterOpensDirective,
    MatFormFieldModule, MatInputModule, MatChipsModule, MatButtonModule, TranslateModule, LoadingComponent
  ],
  templateUrl: './archive.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./archive.component.scss']
})
export class ArchiveComponent implements OnInit {
  filteredProjects: ProjectDto[] = [];
  searchQuery = '';
  isLoading = false;

  private archivedProjects: ProjectDto[] = [];

  constructor(
    private readonly projectService: ProjectService,
    private readonly authService: AuthService,
    private readonly notificationService: NotificationService
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

  /** Keeps closed (won/lost) and archived opportunities, most recent first: archived ones by when they were archived. */
  toArchived(projects: ProjectDto[]): ProjectDto[] {
    const closed = new Set([ProjectStatus.WON, ProjectStatus.LOST]);
    return projects
      .filter(project => !!project.archivedAt || (!!project.status && closed.has(project.status)))
      .sort((a, b) => this.time(b) - this.time(a));
  }

  /** Puts an archived opportunity back in its pipeline column, and takes it off this list unless it is closed. */
  restore(project: ProjectDto, event: Event): void {
    event.stopPropagation();
    this.projectService.restore(project.id!).subscribe({
      next: saved => {
        this.archivedProjects = this.toArchived(this.archivedProjects.map(item => item.id === saved.id ? saved : item));
        this.applySearch();
        this.notificationService.success('archive.restoredSuccess');
      },
      error: () => this.notificationService.error('archive.restoreError')
    });
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

  private time(project: ProjectDto): number {
    const date = project.archivedAt ?? project.startDate;
    return date ? new Date(date).getTime() : 0;
  }
}
