import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ProjectService } from '../../../services/project/project.service';
import { ProjectDto, ProjectNote } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';

@Component({
    selector: 'app-project-detail',
    imports: [
        CommonModule,
        FormsModule,
        RouterModule,
        MatCardModule,
        MatButtonModule,
        MatIconModule,
        MatChipsModule,
        MatProgressSpinnerModule,
        MatDividerModule,
        MatFormFieldModule,
        MatInputModule,
        TranslateModule
    ],
    templateUrl: './project-detail.component.html',
    styleUrls: ['./project-detail.component.scss']
})
export class ProjectDetailComponent implements OnInit, OnDestroy {
  project?: ProjectDto;
  isLoading = false;
  projectId?: number;

  notes: ProjectNote[] = [];
  newNoteContent = '';
  isSavingNote = false;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly projectService: ProjectService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      if (params['id']) {
        this.projectId = +params['id'];
        this.loadProject();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadProject(): void {
    if (!this.projectId) { return; }
    
    this.isLoading = true;
    this.projectService.getById(this.projectId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (project) => {
          if (project) {
            this.project = project;
            this.loadNotes();
          } else {
            this.notificationService.error('errors.projectNotFound');
            this.router.navigate(['/projects']);
          }
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading project:', error);
          this.notificationService.error('errors.loadingProject');
          this.isLoading = false;
        }
      });
  }

  private loadNotes(): void {
    if (!this.projectId) { return; }
    this.projectService.getProjectNotes(this.projectId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (notes) => this.notes = notes,
        error: () => this.notificationService.error('projects.notes.loadError')
      });
  }

  addNote(): void {
    const content = this.newNoteContent.trim();
    if (!content || !this.projectId || this.isSavingNote) { return; }

    this.isSavingNote = true;
    this.projectService.addProjectNote(this.projectId, content)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (note) => {
          this.notes = [note, ...this.notes];
          this.newNoteContent = '';
          this.isSavingNote = false;
        },
        error: () => {
          this.notificationService.error('projects.notes.saveError');
          this.isSavingNote = false;
        }
      });
  }

  deleteNote(noteId: number): void {
    if (!this.projectId || !confirm(this.translate.instant('projects.notes.deleteConfirm'))) { return; }

    this.projectService.deleteProjectNote(this.projectId, noteId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => this.notes = this.notes.filter(n => n.id !== noteId),
        error: () => this.notificationService.error('projects.notes.deleteError')
      });
  }

  onBack(): void {
    this.router.navigate(['/projects']);
  }

  onEdit(): void {
    if (this.project) {
      this.router.navigate(['/projects', this.project.id, 'edit']);
    }
  }

  onDelete(): void {
    if (!this.project) { return; }
    
    if (confirm(this.translate.instant('projects.confirmDelete', { name: this.project.role }))) {
      this.projectService.delete(this.project.id!)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.notificationService.success('projects.deleteSuccess');
            this.router.navigate(['/projects']);
          },
          error: (error) => {
            console.error('Error deleting project:', error);
            this.notificationService.error('errors.deletingProject');
          }
        });
    }
  }

  // Utility methods
  getWorkModeLabel(workMode?: string): string {
    switch (workMode) {
      case 'REMOTE': return this.translate.instant('projects.workModes.remote');
      case 'ONSITE': return this.translate.instant('projects.workModes.onsite');
      case 'HYBRID': return this.translate.instant('projects.workModes.hybrid');
      default: return this.translate.instant('common.notSpecified');
    }
  }

  getWorkModeColor(workMode?: string): string {
    switch (workMode) {
      case 'REMOTE': return 'primary';
      case 'ONSITE': return 'accent';
      case 'HYBRID': return 'warn';
      default: return '';
    }
  }

  getRatingStars(rating?: number): string[] {
    const stars = [];
    for (let i = 1; i <= 5; i++) {
      stars.push(i <= (rating ?? 0) ? 'star' : 'star_border');
    }
    return stars;
  }

  formatDate(dateString?: string): string {
    if (!dateString) { return this.translate.instant('common.notSpecified'); }
    const locale = (localStorage.getItem('indezy-lang') || 'fr') === 'fr' ? 'fr-FR' : 'en-US';
    return new Date(dateString).toLocaleDateString(locale);
  }

  calculateTotalRevenue(): number {
    if (!this.project?.dailyRate || !this.project?.durationInMonths) { return 0; }
    const daysPerMonth = (this.project.daysPerYear ?? 220) / 12;
    return this.project.dailyRate * daysPerMonth * this.project.durationInMonths;
  }

  getProgressPercentage(): number {
    if (!this.project?.totalSteps) { return 0; }
    return Math.round((this.project.completedSteps ?? 0) / this.project.totalSteps * 100);
  }

  openLink(): void {
    if (this.project?.link) {
      window.open(this.project.link, '_blank');
    }
  }
}
