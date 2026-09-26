import { Component, DestroyRef, OnInit, inject, ChangeDetectionStrategy } from '@angular/core';
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
import { MatSliderModule } from '@angular/material/slider';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProjectService } from '../../../services/project/project.service';
import { FreelanceService } from '../../../services/freelance/freelance.service';
import { CommuteService } from '../../../services/commute/commute.service';
import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { SeasonService } from '../../../services/season/season.service';
import { Season } from '../../../models/season.models';
import {
  ProjectFilterValues,
  countActiveFilters,
  filterProjects,
  getProjectStatus,
  sortProjects
} from '../../../services/project/project-filter.util';
import { ProjectDto, User, FreelanceDto, ProjectStatus, PROJECT_STATUS_COLORS } from '../../../models';
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
        MatSliderModule,
        MatCheckboxModule,
        MatExpansionModule,
        MatDividerModule,
        MatButtonToggleModule,
        MatTooltipModule,
        MatPaginatorModule,
        TranslateModule
    ],
    templateUrl: './project-list.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./project-list.component.scss']
})
export class ProjectListComponent implements OnInit {
  projects: ProjectDto[] = [];
  filteredProjects: ProjectDto[] = [];
  /** The slice of filteredProjects shown on the current page. */
  pagedProjects: ProjectDto[] = [];
  pageIndex = 0;
  pageSize = 12;
  readonly pageSizeOptions = [12, 24, 48];
  isLoading = false;
  filterForm: FormGroup;
  currentUser: User | null = null;
  /** Filters start collapsed on phones so the missions are visible without scrolling. */
  filtersExpanded = typeof window === 'undefined' || !window.matchMedia('(max-width: 768px)').matches;

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
  /** Social charges applied on top of the income tax when deriving the reversion rate. */
  static readonly SOCIAL_CHARGES_RATE = 40;

  workModeOptions = [
    { value: 'REMOTE', labelKey: 'projects.workModes.REMOTE' },
    { value: 'ONSITE', labelKey: 'projects.workModes.ONSITE' },
    { value: 'HYBRID', labelKey: 'projects.workModes.HYBRID' }
  ];

  statusOptions = Object.values(ProjectStatus).map(status => ({
    value: status,
    labelKey: 'projects.statuses.' + status
  }));
  readonly statusColors = PROJECT_STATUS_COLORS;

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

  seasons: Season[] = [];

  private readonly destroyRef = inject(DestroyRef);
  private readonly seasonService = inject(SeasonService);

  constructor(
    private readonly projectService: ProjectService,
    private readonly freelanceService: FreelanceService,
    private readonly commuteService: CommuteService,
    private readonly authService: AuthService,
    private readonly fb: FormBuilder,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService,
    private readonly confirmDialog: ConfirmDialogService
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
      season: [''],
      startDateFrom: [''],
      startDateTo: [''],
      endDateFrom: [''],
      endDateTo: [''],
      duration: [''],
      client: [''],
      selectedTechStack: [[]],

      // Sorting
      sortBy: ['startDate'],
      sortOrder: ['desc']
    });
  }

  ngOnInit(): void {
    this.currentUser = this.authService.getUser();
    this.loadProjects();
    this.loadSeasons();
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
        this.isLoading = false;
        this.applyFilters();
      },
      error: (error) => {
        this.isLoading = false;
        this.notificationService.error('projects.loadError');
        console.error('Error loading projects:', error);
      }
    });
  }

  private loadSeasons(): void {
    if (!this.currentUser?.id) {
      return;
    }
    this.seasonService.getByFreelanceId(this.currentUser.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: seasons => this.seasons = seasons,
        error: () => this.seasons = []
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
    this.pageIndex = 0;
    this.updatePage();
  }

  clearFilters(): void {
    this.filterForm.reset({
      sortBy: 'startDate',
      sortOrder: 'desc',
      selectedTechStack: []
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.updatePage();
  }

  private updatePage(): void {
    const lastPage = Math.max(0, Math.ceil(this.filteredProjects.length / this.pageSize) - 1);
    this.pageIndex = Math.min(this.pageIndex, lastPage);
    const start = this.pageIndex * this.pageSize;
    this.pagedProjects = this.filteredProjects.slice(start, start + this.pageSize);
  }

  toggleFilters(): void {
    this.filtersExpanded = !this.filtersExpanded;
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
    this.confirmDialog.confirm({
      messageKey: 'projects.deleteConfirm',
      danger: true
    }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.projectService.delete(projectId).subscribe({
        next: () => {
          this.projects = this.projects.filter(p => p.id !== projectId);
          const pageIndex = this.pageIndex;
          this.applyFilters();
          this.pageIndex = pageIndex;
          this.updatePage();
          this.notificationService.success('projects.deleteSuccess');
        },
        error: (error) => {
          this.notificationService.error('projects.deleteError');
          console.error('Error deleting project:', error);
        }
      });
    });
  }

  /** Downloads a CSV summary of the current year's projects for the accountant. */
  exportSummary(): void {
    if (!this.currentUser?.id) {return;}

    const year = new Date().getFullYear();
    this.projectService.downloadYearlySummary(this.currentUser.id, year).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `indezy-summary-${year}.csv`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.notificationService.error('projects.exportError')
    });
  }

  getWorkModeLabel(workMode: string): string {
    return this.translate.instant('projects.workModes.' + workMode);
  }

  getStatusColor(project: ProjectDto): string {
    return this.statusColors[project.status ?? ProjectStatus.CONTACT];
  }

  /** Delivery phase of a signed mission (upcoming / in progress / completed), null for opportunities. */
  getMissionPhaseKey(project: ProjectDto): string | null {
    if (project.status !== ProjectStatus.WON) {
      return null;
    }
    switch (getProjectStatus(project)) {
      case 'upcoming':
        return 'projects.upcoming';
      case 'inProgress':
        return 'projects.inProgress';
      case 'completed':
        return 'projects.completed';
      default:
        return null;
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
        if (profile.reversionRate !== undefined && profile.reversionRate !== null) {
          this.reversionRate = profile.reversionRate;
          this.useCustomRate = true;
        }
        if (profile.incomeTaxRate !== undefined && profile.incomeTaxRate !== null) {
          this.incomeTaxRate = profile.incomeTaxRate;
        }
      },
      error: (err) => console.error('Error loading freelance profile:', err)
    });
  }

  onReversionRateChange(value: string | number): void {
    const rate = ProjectListComponent.toPercent(value, 100);
    if (rate === null) {
      return;
    }
    this.reversionRate = rate;
    this.useCustomRate = true;
  }

  onIncomeTaxRateChange(value: string | number): void {
    const tax = ProjectListComponent.toPercent(value, 50);
    if (tax === null) {
      return;
    }
    this.incomeTaxRate = tax;
    this.reversionRate = Math.min(100, ProjectListComponent.SOCIAL_CHARGES_RATE + tax);
  }

  /** Parses a percentage typed in a number input, rejecting values outside [0, max]. */
  private static toPercent(value: string | number, max: number): number | null {
    const parsed = typeof value === 'number' ? value : Number.parseFloat(String(value).replace(',', '.'));
    if (!Number.isFinite(parsed) || parsed < 0 || parsed > max) {
      return null;
    }
    return parsed;
  }

  saveReversionRate(): void {
    if (!this.currentUser?.id || !this.freelanceProfile) {
      return;
    }
    const updated = { ...this.freelanceProfile, reversionRate: this.reversionRate, incomeTaxRate: this.incomeTaxRate };
    this.freelanceService.update(this.currentUser.id, updated).subscribe({
      next: (saved) => {
        this.freelanceProfile = saved;
        this.notificationService.success('projects.reversion.saved', 2000);
      },
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
