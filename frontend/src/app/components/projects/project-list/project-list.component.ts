import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { ProjectService, ProjectDto } from '../../../services/project.service';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-project-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatSnackBarModule
  ],
  templateUrl: './project-list.component.html',
  styleUrls: ['./project-list.component.scss']
})
export class ProjectListComponent implements OnInit {
  projects: ProjectDto[] = [];
  filteredProjects: ProjectDto[] = [];
  isLoading = false;
  filterForm: FormGroup;
  currentUser: any;

  workModeOptions = [
    { value: 'REMOTE', label: 'Télétravail' },
    { value: 'ONSITE', label: 'Sur site' },
    { value: 'HYBRID', label: 'Hybride' }
  ];

  constructor(
    private projectService: ProjectService,
    private authService: AuthService,
    private fb: FormBuilder,
    private snackBar: MatSnackBar
  ) {
    this.filterForm = this.fb.group({
      minRate: [''],
      maxRate: [''],
      workMode: [''],
      techStack: ['']
    });
  }

  ngOnInit(): void {
    this.currentUser = this.authService.getUser();
    this.loadProjects();
    this.setupFilters();
  }

  loadProjects(): void {
    if (!this.currentUser?.id) return;

    this.isLoading = true;
    this.projectService.getByFreelanceId(this.currentUser.id).subscribe({
      next: (projects) => {
        this.projects = projects;
        this.filteredProjects = projects;
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.snackBar.open('Erreur lors du chargement des projets', 'Fermer', {
          duration: 3000,
          panelClass: ['error-snackbar']
        });
        console.error('Error loading projects:', error);
      }
    });
  }

  setupFilters(): void {
    this.filterForm.valueChanges.subscribe(() => {
      this.applyFilters();
    });
  }

  applyFilters(): void {
    const filters = this.filterForm.value;
    
    this.filteredProjects = this.projects.filter(project => {
      // Rate filter
      if (filters.minRate && project.dailyRate && project.dailyRate < filters.minRate) {
        return false;
      }
      if (filters.maxRate && project.dailyRate && project.dailyRate > filters.maxRate) {
        return false;
      }
      
      // Work mode filter
      if (filters.workMode && project.workMode !== filters.workMode) {
        return false;
      }
      
      // Tech stack filter
      if (filters.techStack && project.techStack && 
          !project.techStack.toLowerCase().includes(filters.techStack.toLowerCase())) {
        return false;
      }
      
      return true;
    });
  }

  clearFilters(): void {
    this.filterForm.reset();
    this.filteredProjects = this.projects;
  }

  deleteProject(projectId: number): void {
    if (confirm('Êtes-vous sûr de vouloir supprimer ce projet ?')) {
      this.projectService.delete(projectId).subscribe({
        next: () => {
          this.projects = this.projects.filter(p => p.id !== projectId);
          this.applyFilters();
          this.snackBar.open('Projet supprimé avec succès', 'Fermer', {
            duration: 3000,
            panelClass: ['success-snackbar']
          });
        },
        error: (error) => {
          this.snackBar.open('Erreur lors de la suppression', 'Fermer', {
            duration: 3000,
            panelClass: ['error-snackbar']
          });
          console.error('Error deleting project:', error);
        }
      });
    }
  }

  getWorkModeLabel(workMode: string): string {
    const option = this.workModeOptions.find(opt => opt.value === workMode);
    return option ? option.label : workMode;
  }

  getProjectStatusColor(project: ProjectDto): string {
    if (project.startDate) {
      const startDate = new Date(project.startDate);
      const now = new Date();
      
      if (startDate > now) {
        return 'accent'; // Future project
      } else if (project.durationInMonths) {
        const endDate = new Date(startDate);
        endDate.setMonth(endDate.getMonth() + project.durationInMonths);
        
        if (endDate > now) {
          return 'primary'; // Active project
        } else {
          return 'warn'; // Completed project
        }
      }
    }
    return 'primary';
  }

  getProjectStatusText(project: ProjectDto): string {
    if (project.startDate) {
      const startDate = new Date(project.startDate);
      const now = new Date();
      
      if (startDate > now) {
        return 'À venir';
      } else if (project.durationInMonths) {
        const endDate = new Date(startDate);
        endDate.setMonth(endDate.getMonth() + project.durationInMonths);
        
        if (endDate > now) {
          return 'En cours';
        } else {
          return 'Terminé';
        }
      }
    }
    return 'Statut inconnu';
  }
}
