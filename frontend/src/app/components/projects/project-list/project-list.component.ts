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
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSliderModule } from '@angular/material/slider';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
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
    MatSnackBarModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSliderModule,
    MatCheckboxModule,
    MatExpansionModule,
    MatDividerModule
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
  showAdvancedFilters = false;

  workModeOptions = [
    { value: 'REMOTE', label: 'Télétravail' },
    { value: 'ONSITE', label: 'Sur site' },
    { value: 'HYBRID', label: 'Hybride' }
  ];

  statusOptions = [
    { value: 'ACTIVE', label: 'Actif' },
    { value: 'COMPLETED', label: 'Terminé' },
    { value: 'PAUSED', label: 'En pause' },
    { value: 'CANCELLED', label: 'Annulé' }
  ];

  durationOptions = [
    { value: '1-3', label: '1-3 mois' },
    { value: '3-6', label: '3-6 mois' },
    { value: '6-12', label: '6-12 mois' },
    { value: '12+', label: '12+ mois' }
  ];

  techStackOptions = [
    'Angular', 'React', 'Vue.js', 'Node.js', 'Python', 'Java', 'C#', 'PHP',
    'PostgreSQL', 'MongoDB', 'MySQL', 'Docker', 'Kubernetes', 'AWS', 'Azure'
  ];

  sortOptions = [
    { value: 'startDate', label: 'Date de début' },
    { value: 'dailyRate', label: 'TJM' },
    { value: 'role', label: 'Nom du projet' },
    { value: 'clientName', label: 'Client' }
  ];

  constructor(
    private projectService: ProjectService,
    private authService: AuthService,
    private fb: FormBuilder,
    private snackBar: MatSnackBar
  ) {
    this.filterForm = this.fb.group({
      // Basic filters
      searchQuery: [''],
      minRate: [''],
      maxRate: [''],
      workMode: [''],
      techStack: [''],

      // Advanced filters
      status: [''],
      startDateFrom: [''],
      startDateTo: [''],
      endDateFrom: [''],
      endDateTo: [''],
      duration: [''],
      client: [''],
      location: [''],
      selectedTechStack: [[]],

      // Sorting
      sortBy: ['startDate'],
      sortOrder: ['desc']
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

    let filtered = this.projects.filter(project => {
      // Search query filter
      if (filters.searchQuery && filters.searchQuery.trim()) {
        const query = filters.searchQuery.toLowerCase();
        const searchableText = [
          project.role,
          project.clientName,
          project.description,
          project.techStack
        ].filter(Boolean).join(' ').toLowerCase();

        if (!searchableText.includes(query)) {
          return false;
        }
      }

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

      // TODO: Implement status filter when backend supports it
      // if (filters.status && project.status !== filters.status) {
      //   return false;
      // }

      // Tech stack filter (legacy)
      if (filters.techStack && project.techStack &&
          !project.techStack.toLowerCase().includes(filters.techStack.toLowerCase())) {
        return false;
      }

      // Selected tech stack filter (new multi-select)
      if (filters.selectedTechStack && filters.selectedTechStack.length > 0) {
        const projectTechStack = project.techStack?.toLowerCase() || '';
        const hasRequiredTech = filters.selectedTechStack.some((tech: string) =>
          projectTechStack.includes(tech.toLowerCase())
        );
        if (!hasRequiredTech) {
          return false;
        }
      }

      // Date filters
      if (filters.startDateFrom && project.startDate) {
        const startDate = new Date(project.startDate);
        const filterDate = new Date(filters.startDateFrom);
        if (startDate < filterDate) {
          return false;
        }
      }

      if (filters.startDateTo && project.startDate) {
        const startDate = new Date(project.startDate);
        const filterDate = new Date(filters.startDateTo);
        if (startDate > filterDate) {
          return false;
        }
      }

      // TODO: Implement endDate filtering when backend supports it
      // if (filters.endDateFrom && project.endDate) {
      //   const endDate = new Date(project.endDate);
      //   const filterDate = new Date(filters.endDateFrom);
      //   if (endDate < filterDate) {
      //     return false;
      //   }
      // }

      // if (filters.endDateTo && project.endDate) {
      //   const endDate = new Date(project.endDate);
      //   const filterDate = new Date(filters.endDateTo);
      //   if (endDate > filterDate) {
      //     return false;
      //   }
      // }

      // Duration filter
      if (filters.duration && project.durationInMonths) {
        const duration = project.durationInMonths;
        switch (filters.duration) {
          case '1-3':
            if (duration < 1 || duration > 3) return false;
            break;
          case '3-6':
            if (duration < 3 || duration > 6) return false;
            break;
          case '6-12':
            if (duration < 6 || duration > 12) return false;
            break;
          case '12+':
            if (duration < 12) return false;
            break;
        }
      }

      // Client filter
      if (filters.client && filters.client.trim()) {
        const clientQuery = filters.client.toLowerCase();
        if (!project.clientName?.toLowerCase().includes(clientQuery)) {
          return false;
        }
      }

      // TODO: Implement location filtering when backend supports it
      // if (filters.location && filters.location.trim()) {
      //   const locationQuery = filters.location.toLowerCase();
      //   const projectLocation = project.location?.toLowerCase() || '';
      //   if (!projectLocation.includes(locationQuery)) {
      //     return false;
      //   }
      // }

      return true;
    });

    // Apply sorting
    if (filters.sortBy) {
      filtered = this.sortProjects(filtered, filters.sortBy, filters.sortOrder);
    }

    this.filteredProjects = filtered;
  }

  private sortProjects(projects: ProjectDto[], sortBy: string, sortOrder: string): ProjectDto[] {
    return projects.sort((a, b) => {
      let aValue: any;
      let bValue: any;

      switch (sortBy) {
        case 'startDate':
          aValue = a.startDate ? new Date(a.startDate).getTime() : 0;
          bValue = b.startDate ? new Date(b.startDate).getTime() : 0;
          break;
        case 'dailyRate':
          aValue = a.dailyRate || 0;
          bValue = b.dailyRate || 0;
          break;
        case 'role':
          aValue = a.role?.toLowerCase() || '';
          bValue = b.role?.toLowerCase() || '';
          break;
        case 'clientName':
          aValue = a.clientName?.toLowerCase() || '';
          bValue = b.clientName?.toLowerCase() || '';
          break;
        default:
          return 0;
      }

      if (aValue < bValue) {
        return sortOrder === 'asc' ? -1 : 1;
      }
      if (aValue > bValue) {
        return sortOrder === 'asc' ? 1 : -1;
      }
      return 0;
    });
  }

  clearFilters(): void {
    this.filterForm.reset({
      sortBy: 'startDate',
      sortOrder: 'desc'
    });
    this.filteredProjects = this.projects;
  }

  toggleAdvancedFilters(): void {
    this.showAdvancedFilters = !this.showAdvancedFilters;
  }

  addTechStack(tech: string): void {
    const selectedTechStack = this.filterForm.get('selectedTechStack')?.value || [];
    if (!selectedTechStack.includes(tech)) {
      selectedTechStack.push(tech);
      this.filterForm.get('selectedTechStack')?.setValue([...selectedTechStack]);
    }
  }

  removeTechStack(tech: string): void {
    const selectedTechStack = this.filterForm.get('selectedTechStack')?.value || [];
    const index = selectedTechStack.indexOf(tech);
    if (index >= 0) {
      selectedTechStack.splice(index, 1);
      this.filterForm.get('selectedTechStack')?.setValue([...selectedTechStack]);
    }
  }

  getFilterCount(): number {
    const filters = this.filterForm.value;
    let count = 0;

    if (filters.searchQuery?.trim()) count++;
    if (filters.minRate) count++;
    if (filters.maxRate) count++;
    if (filters.workMode) count++;
    if (filters.status) count++;
    if (filters.techStack?.trim()) count++;
    if (filters.selectedTechStack?.length > 0) count++;
    if (filters.startDateFrom) count++;
    if (filters.startDateTo) count++;
    if (filters.endDateFrom) count++;
    if (filters.endDateTo) count++;
    if (filters.duration) count++;
    if (filters.client?.trim()) count++;
    if (filters.location?.trim()) count++;

    return count;
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
