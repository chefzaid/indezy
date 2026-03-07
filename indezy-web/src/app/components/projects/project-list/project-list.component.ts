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
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProjectService } from '../../../services/project/project.service';
import { AuthService } from '../../../services/auth/auth.service';
import { ProjectDto, User } from '../../../models';

@Component({
    selector: 'app-project-list',
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
        MatDividerModule,
        TranslateModule
    ],
    templateUrl: './project-list.component.html',
    styleUrls: ['./project-list.component.scss']
})
export class ProjectListComponent implements OnInit {
  projects: ProjectDto[] = [];
  filteredProjects: ProjectDto[] = [];
  isLoading = false;
  filterForm: FormGroup;
  currentUser: User | null = null;
  showAdvancedFilters = false;

  workModeOptions = [
    { value: 'REMOTE', labelKey: 'projects.workModes.REMOTE' },
    { value: 'ONSITE', labelKey: 'projects.workModes.ONSITE' },
    { value: 'HYBRID', labelKey: 'projects.workModes.HYBRID' }
  ];

  statusOptions = [
    { value: 'ACTIVE', labelKey: 'projects.statuses.ACTIVE' },
    { value: 'COMPLETED', labelKey: 'projects.statuses.COMPLETED' },
    { value: 'PAUSED', labelKey: 'projects.statuses.PAUSED' },
    { value: 'CANCELLED', labelKey: 'projects.statuses.CANCELLED' }
  ];

  durationOptions = [
    { value: '1-3', labelKey: 'projects.durations.1-3' },
    { value: '3-6', labelKey: 'projects.durations.3-6' },
    { value: '6-12', labelKey: 'projects.durations.6-12' },
    { value: '12+', labelKey: 'projects.durations.12+' }
  ];

  techStackOptions = [
    'Angular', 'React', 'Vue.js', 'Node.js', 'Python', 'Java', 'C#', 'PHP',
    'PostgreSQL', 'MongoDB', 'MySQL', 'Docker', 'Kubernetes', 'AWS', 'Azure'
  ];

  sortOptions = [
    { value: 'startDate', labelKey: 'projects.startDate' },
    { value: 'dailyRate', labelKey: 'projects.dailyRate' },
    { value: 'role', labelKey: 'projects.projectName' },
    { value: 'clientName', labelKey: 'projects.client' }
  ];

  constructor(
    private readonly projectService: ProjectService,
    private readonly authService: AuthService,
    private readonly fb: FormBuilder,
    private readonly snackBar: MatSnackBar,
    private readonly translate: TranslateService
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
    if (!this.currentUser?.id) {
      return;
    }

    this.isLoading = true;
    this.projectService.getByFreelanceId(this.currentUser.id).subscribe({
      next: (projects) => {
        this.projects = projects;
        this.filteredProjects = projects;
        this.isLoading = false;
      },
      error: (error) => {
        this.isLoading = false;
        this.snackBar.open(this.translate.instant('projects.loadError'), this.translate.instant('common.close'), {
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

    let filtered = this.projects.filter(project =>
      this.matchesSearchQuery(project, filters) &&
      this.matchesRate(project, filters) &&
      this.matchesWorkMode(project, filters) &&
      this.matchesTechStack(project, filters) &&
      this.matchesSelectedTechStack(project, filters) &&
      this.matchesDateRange(project, filters) &&
      this.matchesDuration(project, filters) &&
      this.matchesClient(project, filters)
    );

    if (filters.sortBy) {
      filtered = this.sortProjects(filtered, filters.sortBy, filters.sortOrder);
    }

    this.filteredProjects = filtered;
  }

  private matchesSearchQuery(project: ProjectDto, filters: any): boolean {
    if (!filters.searchQuery?.trim()) {
      return true;
    }
    const query = filters.searchQuery.toLowerCase();
    const searchableText = [
      project.role, project.clientName, project.description, project.techStack
    ].filter(Boolean).join(' ').toLowerCase();
    return searchableText.includes(query);
  }

  private matchesRate(project: ProjectDto, filters: any): boolean {
    if (filters.minRate && project.dailyRate && project.dailyRate < filters.minRate) {
      return false;
    }
    if (filters.maxRate && project.dailyRate && project.dailyRate > filters.maxRate) {
      return false;
    }
    return true;
  }

  private matchesWorkMode(project: ProjectDto, filters: any): boolean {
    return !filters.workMode || project.workMode === filters.workMode;
  }

  private matchesTechStack(project: ProjectDto, filters: any): boolean {
    if (filters.techStack && project.techStack &&
        !project.techStack.toLowerCase().includes(filters.techStack.toLowerCase())) {
      return false;
    }
    return true;
  }

  private matchesSelectedTechStack(project: ProjectDto, filters: any): boolean {
    if (!filters.selectedTechStack || filters.selectedTechStack.length === 0) {
      return true;
    }
    const projectTechStack = project.techStack?.toLowerCase() ?? '';
    return filters.selectedTechStack.some((tech: string) =>
      projectTechStack.includes(tech.toLowerCase())
    );
  }

  private matchesDateRange(project: ProjectDto, filters: any): boolean {
    if (filters.startDateFrom && project.startDate) {
      if (new Date(project.startDate) < new Date(filters.startDateFrom)) {
        return false;
      }
    }
    if (filters.startDateTo && project.startDate) {
      if (new Date(project.startDate) > new Date(filters.startDateTo)) {
        return false;
      }
    }
    return true;
  }

  private matchesDuration(project: ProjectDto, filters: any): boolean {
    if (!filters.duration || !project.durationInMonths) {
      return true;
    }
    const duration = project.durationInMonths;
    const ranges: Record<string, [number, number]> = {
      '1-3': [1, 3], '3-6': [3, 6], '6-12': [6, 12], '12+': [12, Infinity]
    };
    const range = ranges[filters.duration];
    if (!range) {
      return true;
    }
    return duration >= range[0] && duration <= range[1];
  }

  private matchesClient(project: ProjectDto, filters: any): boolean {
    if (!filters.client?.trim()) {
      return true;
    }
    return project.clientName?.toLowerCase().includes(filters.client.toLowerCase()) ?? false;
  }

  private sortProjects(projects: ProjectDto[], sortBy: string, sortOrder: string): ProjectDto[] {
    return projects.sort((a, b) => {
      let aValue: string | number | Date;
      let bValue: string | number | Date;

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

    if (filters.searchQuery?.trim()) {
      count++;
    }
    if (filters.minRate) {
      count++;
    }
    if (filters.maxRate) {
      count++;
    }
    if (filters.workMode) {
      count++;
    }
    if (filters.status) {
      count++;
    }
    if (filters.techStack?.trim()) {
      count++;
    }
    if (filters.selectedTechStack?.length > 0) {
      count++;
    }
    if (filters.startDateFrom) {
      count++;
    }
    if (filters.startDateTo) {
      count++;
    }
    if (filters.endDateFrom) {
      count++;
    }
    if (filters.endDateTo) {
      count++;
    }
    if (filters.duration) {
      count++;
    }
    if (filters.client?.trim()) {
      count++;
    }
    if (filters.location?.trim()) {
      count++;
    }

    return count;
  }

  deleteProject(projectId: number): void {
    if (confirm(this.translate.instant('projects.deleteConfirm'))) {
      this.projectService.delete(projectId).subscribe({
        next: () => {
          this.projects = this.projects.filter(p => p.id !== projectId);
          this.applyFilters();
          this.snackBar.open(this.translate.instant('projects.deleteSuccess'), this.translate.instant('common.close'), {
            duration: 3000,
            panelClass: ['success-snackbar']
          });
        },
        error: (error) => {
          this.snackBar.open(this.translate.instant('projects.deleteError'), this.translate.instant('common.close'), {
            duration: 3000,
            panelClass: ['error-snackbar']
          });
          console.error('Error deleting project:', error);
        }
      });
    }
  }

  getWorkModeLabel(workMode: string): string {
    return this.translate.instant('projects.workModes.' + workMode);
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
        return this.translate.instant('projects.upcoming');
      } else if (project.durationInMonths) {
        const endDate = new Date(startDate);
        endDate.setMonth(endDate.getMonth() + project.durationInMonths);
        
        if (endDate > now) {
          return this.translate.instant('projects.inProgress');
        } else {
          return this.translate.instant('projects.completed');
        }
      }
    }
    return this.translate.instant('projects.unknownStatus');
  }
}
