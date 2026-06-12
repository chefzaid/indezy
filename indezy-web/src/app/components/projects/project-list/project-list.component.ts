import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSliderModule } from '@angular/material/slider';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProjectService } from '../../../services/project/project.service';
import { FreelanceService } from '../../../services/freelance/freelance.service';
import { CommuteService } from '../../../services/commute/commute.service';
import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';
import {
  ProjectFilterValues,
  countActiveFilters,
  filterProjects,
  getProjectStatus,
  sortProjects
} from '../../../services/project/project-filter.util';
import { ProjectDto, User, FreelanceDto } from '../../../models';
import { TravelMode, ProjectCommuteDto, CommuteInfoDto } from '../../../models/commute.models';

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
        MatButtonToggleModule,
        MatTooltipModule,
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

  // Commute sorting
  isCommuteSortActive = false;
  isCommuteLoading = false;
  commuteTravelMode: TravelMode = 'DRIVING';
  commuteData: Map<number, CommuteInfoDto> = new Map();

  // Reversion rate
  freelanceProfile: FreelanceDto | null = null;
  reversionRate = 45;
  incomeTaxRate = 5;
  useCustomRate = false;
  readonly DEFAULT_DAYS_PER_YEAR = 218;

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
    { value: 'clientName', labelKey: 'projects.client' },
    { value: 'commuteTime', labelKey: 'projects.commuteTime' }
  ];

  private readonly destroyRef = inject(DestroyRef);

  constructor(
    private readonly projectService: ProjectService,
    private readonly freelanceService: FreelanceService,
    private readonly commuteService: CommuteService,
    private readonly authService: AuthService,
    private readonly fb: FormBuilder,
    private readonly notificationService: NotificationService,
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
    this.loadFreelanceProfile();
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
        this.notificationService.error('projects.loadError');
        console.error('Error loading projects:', error);
      }
    });
  }

  setupFilters(): void {
    this.filterForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.applyFilters());
  }

  applyFilters(): void {
    const filters: ProjectFilterValues = this.filterForm.value;
    let filtered = filterProjects(this.projects, filters);
    if (filters.sortBy) {
      filtered = sortProjects(filtered, filters.sortBy, filters.sortOrder ?? 'desc', this.commuteData);
    }
    this.filteredProjects = filtered;
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
    const selectedTechStack: string[] = this.filterForm.get('selectedTechStack')?.value || [];
    if (!selectedTechStack.includes(tech)) {
      this.filterForm.get('selectedTechStack')?.setValue([...selectedTechStack, tech]);
    }
  }

  removeTechStack(tech: string): void {
    const selectedTechStack: string[] = this.filterForm.get('selectedTechStack')?.value || [];
    if (selectedTechStack.includes(tech)) {
      this.filterForm.get('selectedTechStack')?.setValue(selectedTechStack.filter(t => t !== tech));
    }
  }

  getFilterCount(): number {
    return countActiveFilters(this.filterForm.value);
  }

  deleteProject(projectId: number): void {
    if (confirm(this.translate.instant('projects.deleteConfirm'))) {
      this.projectService.delete(projectId).subscribe({
        next: () => {
          this.projects = this.projects.filter(p => p.id !== projectId);
          this.applyFilters();
          this.notificationService.success('projects.deleteSuccess');
        },
        error: (error) => {
          this.notificationService.error('projects.deleteError');
          console.error('Error deleting project:', error);
        }
      });
    }
  }

  getWorkModeLabel(workMode: string): string {
    return this.translate.instant('projects.workModes.' + workMode);
  }

  getProjectStatusColor(project: ProjectDto): string {
    switch (getProjectStatus(project)) {
      case 'upcoming':
        return 'accent';
      case 'completed':
        return 'warn';
      default:
        return 'primary';
    }
  }

  getProjectStatusText(project: ProjectDto): string {
    switch (getProjectStatus(project)) {
      case 'upcoming':
        return this.translate.instant('projects.upcoming');
      case 'inProgress':
        return this.translate.instant('projects.inProgress');
      case 'completed':
        return this.translate.instant('projects.completed');
      default:
        return this.translate.instant('projects.unknownStatus');
    }
  }

  // Commute sorting methods
  onSortByChange(sortBy: string): void {
    if (sortBy === 'commuteTime' && !this.isCommuteSortActive) {
      this.loadCommuteData();
    }
  }

  onTravelModeChange(mode: TravelMode): void {
    this.commuteTravelMode = mode;
    if (this.isCommuteSortActive || this.filterForm.get('sortBy')?.value === 'commuteTime') {
      this.loadCommuteData();
    }
  }

  loadCommuteData(): void {
    if (!this.currentUser?.id) {
      return;
    }

    this.isCommuteLoading = true;
    this.commuteService.getProjectsSortedByCommute(this.currentUser.id, this.commuteTravelMode).subscribe({
      next: (results: ProjectCommuteDto[]) => {
        this.commuteData.clear();
        for (const result of results) {
          if (result.commute && result.project.id) {
            this.commuteData.set(result.project.id, result.commute);
          }
        }
        this.isCommuteSortActive = true;
        this.isCommuteLoading = false;
        this.applyFilters();
      },
      error: (error) => {
        this.isCommuteLoading = false;
        this.notificationService.error('projects.commuteLoadError');
        console.error('Error loading commute data:', error);
      }
    });
  }

  getCommuteInfo(projectId: number | undefined): CommuteInfoDto | undefined {
    if (!projectId) {
      return undefined;
    }
    return this.commuteData.get(projectId);
  }

  getCommuteIcon(mode: TravelMode): string {
    return mode === 'DRIVING' ? 'directions_car' : 'directions_transit';
  }

  // Reversion rate methods
  private loadFreelanceProfile(): void {
    if (!this.currentUser?.id) {
      return;
    }
    this.freelanceService.getById(this.currentUser.id).subscribe({
      next: (profile) => {
        this.freelanceProfile = profile;
        if (profile.reversionRate !== undefined) {
          this.reversionRate = profile.reversionRate;
          this.useCustomRate = true;
        }
        if (profile.incomeTaxRate !== undefined) {
          this.incomeTaxRate = profile.incomeTaxRate;
        }
      },
      error: (err) => console.error('Error loading freelance profile:', err)
    });
  }

  onReversionRateChange(value: number): void {
    this.reversionRate = value;
    this.useCustomRate = true;
  }

  onIncomeTaxRateChange(value: number): void {
    this.incomeTaxRate = value;
    this.reversionRate = 40 + value;
  }

  saveReversionRate(): void {
    if (!this.currentUser?.id || !this.freelanceProfile) {
      return;
    }
    const updated = { ...this.freelanceProfile, reversionRate: this.reversionRate, incomeTaxRate: this.incomeTaxRate };
    this.freelanceService.update(this.currentUser.id, updated).subscribe({
      next: () => this.notificationService.success('projects.reversion.saved', 2000),
      error: () => this.notificationService.error('projects.reversion.saveError')
    });
  }

  getNetDailyRate(project: ProjectDto): number {
    if (!project.dailyRate) {
      return 0;
    }
    return Math.round(project.dailyRate * (1 - this.reversionRate / 100));
  }

  getNetMonthly(project: ProjectDto): number {
    const daysPerYear = project.daysPerYear || this.DEFAULT_DAYS_PER_YEAR;
    return Math.round(this.getNetDailyRate(project) * daysPerYear / 12);
  }

  getNetYearly(project: ProjectDto): number {
    const daysPerYear = project.daysPerYear || this.DEFAULT_DAYS_PER_YEAR;
    return Math.round(this.getNetDailyRate(project) * daysPerYear);
  }
}
