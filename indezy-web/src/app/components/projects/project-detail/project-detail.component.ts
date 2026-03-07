import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ProjectService } from '../../../services/project/project.service';
import { ProjectDto } from '../../../models';

@Component({
    selector: 'app-project-detail',
    imports: [
        CommonModule,
        RouterModule,
        MatCardModule,
        MatButtonModule,
        MatIconModule,
        MatChipsModule,
        MatProgressSpinnerModule,
        MatDividerModule,
        TranslateModule
    ],
    templateUrl: './project-detail.component.html',
    styleUrls: ['./project-detail.component.scss']
})
export class ProjectDetailComponent implements OnInit, OnDestroy {
  project?: ProjectDto;
  isLoading = false;
  projectId?: number;

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly projectService: ProjectService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly snackBar: MatSnackBar,
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
          } else {
            this.snackBar.open(this.translate.instant('errors.projectNotFound'), this.translate.instant('common.close'), { duration: 3000 });
            this.router.navigate(['/projects']);
          }
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading project:', error);
          this.snackBar.open(this.translate.instant('errors.loadingProject'), this.translate.instant('common.close'), { duration: 3000 });
          this.isLoading = false;
        }
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
            this.snackBar.open(this.translate.instant('projects.deleteSuccess'), this.translate.instant('common.close'), { duration: 3000 });
            this.router.navigate(['/projects']);
          },
          error: (error) => {
            console.error('Error deleting project:', error);
            this.snackBar.open(this.translate.instant('errors.deletingProject'), this.translate.instant('common.close'), { duration: 3000 });
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
