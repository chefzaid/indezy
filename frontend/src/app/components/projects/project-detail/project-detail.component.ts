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

import { ProjectService, ProjectDto } from '../../../services/project.service';

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
        MatDividerModule
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
    private readonly snackBar: MatSnackBar
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
    if (!this.projectId) return;
    
    this.isLoading = true;
    this.projectService.getById(this.projectId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (project) => {
          if (project) {
            this.project = project;
          } else {
            this.snackBar.open('Projet non trouvé', 'Fermer', { duration: 3000 });
            this.router.navigate(['/projects']);
          }
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading project:', error);
          this.snackBar.open('Erreur lors du chargement du projet', 'Fermer', { duration: 3000 });
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
    if (!this.project) return;
    
    if (confirm(`Êtes-vous sûr de vouloir supprimer le projet "${this.project.role}" ?`)) {
      this.projectService.delete(this.project.id!)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.snackBar.open('Projet supprimé avec succès', 'Fermer', { duration: 3000 });
            this.router.navigate(['/projects']);
          },
          error: (error) => {
            console.error('Error deleting project:', error);
            this.snackBar.open('Erreur lors de la suppression du projet', 'Fermer', { duration: 3000 });
          }
        });
    }
  }

  // Utility methods
  getWorkModeLabel(workMode?: string): string {
    switch (workMode) {
      case 'REMOTE': return 'Télétravail';
      case 'ONSITE': return 'Sur site';
      case 'HYBRID': return 'Hybride';
      default: return 'Non spécifié';
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
      stars.push(i <= (rating || 0) ? 'star' : 'star_border');
    }
    return stars;
  }

  formatDate(dateString?: string): string {
    if (!dateString) return 'Non spécifié';
    return new Date(dateString).toLocaleDateString('fr-FR');
  }

  calculateTotalRevenue(): number {
    if (!this.project?.dailyRate || !this.project?.durationInMonths) return 0;
    const daysPerMonth = (this.project.daysPerYear || 220) / 12;
    return this.project.dailyRate * daysPerMonth * this.project.durationInMonths;
  }

  getProgressPercentage(): number {
    if (!this.project?.totalSteps) return 0;
    return Math.round((this.project.completedSteps || 0) / this.project.totalSteps * 100);
  }

  openLink(): void {
    if (this.project?.link) {
      window.open(this.project.link, '_blank');
    }
  }
}
