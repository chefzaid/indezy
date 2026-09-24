import { Component, OnInit, OnDestroy, ChangeDetectionStrategy } from '@angular/core';

import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subject, forkJoin, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ProjectService } from '../../../services/project/project.service';
import { ClientService } from '../../../services/client/client.service';
import { SourceService } from '../../../services/source/source.service';
import { SeasonService } from '../../../services/season/season.service';
import { Season } from '../../../models/season.models';
import { AuthService } from '../../../services/auth/auth.service';
import { ProjectDto, ClientDto, SourceDto, User, ProjectStatus, LOST_REASONS, PROJECT_STATUS_COLORS } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { fromIsoDate, toIsoDate } from '../../../shared/locale/app-locale';
import { blankToNull } from '../../../shared/utils/form-payload';

@Component({
    selector: 'app-project-form',
    imports: [
    RouterModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatDatepickerModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule
],
    templateUrl: './project-form.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./project-form.component.scss']
})
export class ProjectFormComponent implements OnInit, OnDestroy {
  projectForm: FormGroup;
  /** Final clients (companies the mission is delivered for). */
  clients: ClientDto[] = [];
  /** Intermediaries (ESN, agencies) that can sit between the freelance and the final client. */
  intermediaries: ClientDto[] = [];
  sources: SourceDto[] = [];
  seasons: Season[] = [];
  isLoading = false;
  isSubmitting = false;
  isEditMode = false;
  projectId?: number;
  currentUser: User | null = null;
  /** The project as loaded, so fields this form does not edit (documents...) survive an update. */
  private loadedProject?: ProjectDto;

  readonly statusOptions = Object.values(ProjectStatus);
  readonly statusColors = PROJECT_STATUS_COLORS;
  readonly lostReasons = LOST_REASONS;

  workModeOptions = [
    { value: 'REMOTE', labelKey: 'projects.workModes.remote' },
    { value: 'ONSITE', labelKey: 'projects.workModes.onsite' },
    { value: 'HYBRID', labelKey: 'projects.workModes.hybrid' }
  ];

  ratingOptions = [
    { value: 1, labelKey: 'projects.ratings.veryBad' },
    { value: 2, labelKey: 'projects.ratings.bad' },
    { value: 3, labelKey: 'projects.ratings.average' },
    { value: 4, labelKey: 'projects.ratings.good' },
    { value: 5, labelKey: 'projects.ratings.excellent' }
  ];

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly fb: FormBuilder,
    private readonly projectService: ProjectService,
    private readonly clientService: ClientService,
    private readonly sourceService: SourceService,
    private readonly seasonService: SeasonService,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService
  ) {
    this.projectForm = this.createForm();
  }

  ngOnInit(): void {
    this.currentUser = this.authService.getUser();
    this.loadReferenceData();

    const requestedStatus = this.route.snapshot.queryParamMap.get('status');
    if (requestedStatus && this.statusOptions.includes(requestedStatus as ProjectStatus)) {
      this.projectForm.patchValue({ status: requestedStatus });
    }

    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      if (params['id']) {
        this.projectId = +params['id'];
        this.isEditMode = true;
        this.loadProject();
      }
    });

    this.projectForm.get('status')?.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe(status => {
        if (status !== ProjectStatus.LOST) {
          this.projectForm.get('lostReason')?.setValue(null, { emitEvent: false });
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private createForm(): FormGroup {
    return this.fb.group({
      role: ['', [Validators.required, Validators.minLength(2)]],
      description: [''],
      techStack: [''],
      dailyRate: ['', [Validators.required, Validators.min(1)]],
      clientDailyRate: ['', [Validators.min(1)]],
      askedDailyRate: ['', [Validators.min(1)]],
      offeredDailyRate: ['', [Validators.min(1)]],
      workMode: [''],
      remoteDaysPerMonth: [''],
      onsiteDaysPerMonth: [''],
      advantages: [''],
      startDate: [''],
      durationInMonths: ['', [Validators.min(1)]],
      orderRenewalInMonths: [''],
      daysPerYear: [218, [Validators.min(1), Validators.max(365)]],
      link: [''],
      personalRating: [''],
      notes: [''],
      isFavorite: [false],
      status: [ProjectStatus.CONTACT, Validators.required],
      lostReason: [null],
      clientId: [null, Validators.required],
      middlemanId: [null],
      sourceId: [null],
      seasonId: [null]
    });
  }

  private loadReferenceData(): void {
    const freelanceId = this.currentUser?.id;
    if (!freelanceId) { return; }

    forkJoin({
      clients: this.clientService.getByFreelanceId(freelanceId),
      sources: this.sourceService.getByFreelanceId(freelanceId),
      seasons: this.seasonService.getByFreelanceId(freelanceId)
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: ({ clients, sources, seasons }) => {
          const byName = (a: ClientDto, b: ClientDto): number => a.companyName.localeCompare(b.companyName);
          this.clients = clients.filter(client => client.isFinal !== false).sort(byName);
          this.intermediaries = clients.filter(client => client.isFinal === false).sort(byName);
          this.sources = [...sources].sort((a, b) => a.name.localeCompare(b.name));
          this.seasons = seasons;
          // A new opportunity joins the running season unless another one is picked.
          const seasonControl = this.projectForm.get('seasonId');
          if (!this.isEditMode && !seasonControl?.value) {
            seasonControl?.setValue(seasons.find(season => season.active)?.id ?? null);
          }
        },
        error: (error) => {
          console.error('Error loading clients and sources:', error);
          this.notificationService.error('errors.loadingClients');
        }
      });
  }

  private loadProject(): void {
    if (!this.projectId) { return; }
    
    this.isLoading = true;
    this.projectService.getById(this.projectId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (project) => {
          if (project) {
            this.loadedProject = project;
            this.projectForm.patchValue({
              role: project.role,
              description: project.description,
              techStack: project.techStack,
              dailyRate: project.dailyRate,
              clientDailyRate: project.clientDailyRate,
              askedDailyRate: project.askedDailyRate,
              offeredDailyRate: project.offeredDailyRate,
              workMode: project.workMode,
              remoteDaysPerMonth: project.remoteDaysPerMonth,
              onsiteDaysPerMonth: project.onsiteDaysPerMonth,
              advantages: project.advantages,
              startDate: fromIsoDate(project.startDate),
              durationInMonths: project.durationInMonths,
              orderRenewalInMonths: project.orderRenewalInMonths,
              daysPerYear: project.daysPerYear,
              link: project.link,
              personalRating: project.personalRating,
              notes: project.notes,
              isFavorite: project.isFavorite ?? false,
              status: project.status ?? ProjectStatus.CONTACT,
              lostReason: project.lostReason ?? null,
              clientId: project.clientId ?? null,
              middlemanId: project.middlemanId ?? null,
              sourceId: project.sourceId ?? null,
              seasonId: project.seasonId ?? null
            });
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

  onSubmit(): void {
    if (this.projectForm.valid && !this.isSubmitting) {
      this.isSubmitting = true;
      const formValue = blankToNull(this.projectForm.value);

      // PUT replaces the whole project: start from the loaded project so the fields this
      // form does not edit (documents, board position...) are sent back unchanged.
      const projectData: ProjectDto = {
        ...(this.loadedProject ?? {}),
        ...formValue,
        lostReason: formValue.status === ProjectStatus.LOST ? formValue.lostReason ?? undefined : undefined,
        freelanceId: this.loadedProject?.freelanceId ?? this.currentUser?.id,
        startDate: toIsoDate(formValue.startDate)
      };

      const operation = this.isEditMode
        ? this.projectService.update(this.projectId!, projectData)
        : this.projectService.create(projectData);

      operation.pipe(takeUntil(this.destroy$)).subscribe({
        next: (saved) => {
          this.notificationService.success(this.isEditMode ? 'projects.updateSuccess' : 'projects.createSuccess');
          const savedId = saved?.id ?? this.projectId;
          this.router.navigate(savedId ? ['/projects', savedId] : ['/projects']);
        },
        error: (error) => {
          console.error('Error saving project:', error);
          this.notificationService.error(this.isEditMode ? 'errors.updatingProject' : 'errors.creatingProject');
          this.isSubmitting = false;
        }
      });
    } else {
      this.markFormGroupTouched();
    }
  }

  onCancel(): void {
    this.router.navigate(this.isEditMode && this.projectId ? ['/projects', this.projectId] : ['/projects']);
  }

  private markFormGroupTouched(): void {
    Object.keys(this.projectForm.controls).forEach(key => {
      const control = this.projectForm.get(key);
      control?.markAsTouched();
    });
  }

  getFieldError(fieldName: string): string {
    const control = this.projectForm.get(fieldName);
    if (control?.errors && control.touched) {
      if (control.errors['required']) {
        return this.translate.instant('errors.fieldRequired');
      }
      if (control.errors['minlength']) {
        return this.translate.instant('errors.minLength', { length: control.errors['minlength'].requiredLength });
      }
      if (control.errors['min']) {
        return this.translate.instant('errors.minValue', { value: control.errors['min'].min });
      }
      if (control.errors['max']) {
        return this.translate.instant('errors.maxValue', { value: control.errors['max'].max });
      }
    }
    return '';
  }

  get pageTitle(): string {
    return this.isEditMode ? this.translate.instant('projects.form.editTitle') : this.translate.instant('projects.form.newTitle');
  }

  get submitButtonText(): string {
    return this.isEditMode ? this.translate.instant('common.edit') : this.translate.instant('common.create');
  }

  setMaxWorkableDays(): void {
    const now = new Date();
    const year = now.getMonth() >= 10 ? now.getFullYear() + 1 : now.getFullYear();
    const maxDays = this.getMaxWorkableDays(year);
    this.projectForm.get('daysPerYear')?.setValue(maxDays);
  }

  private getMaxWorkableDays(year: number): number {
    let businessDays = 0;
    const date = new Date(year, 0, 1);
    while (date.getFullYear() === year) {
      const day = date.getDay();
      if (day !== 0 && day !== 6) {
        businessDays++;
      }
      date.setDate(date.getDate() + 1);
    }

    const holidays = this.getFrenchPublicHolidays(year);
    let holidaysOnBusinessDays = 0;
    for (const holiday of holidays) {
      const day = holiday.getDay();
      if (day !== 0 && day !== 6) {
        holidaysOnBusinessDays++;
      }
    }

    return businessDays - holidaysOnBusinessDays;
  }

  private getFrenchPublicHolidays(year: number): Date[] {
    const easter = this.computeEasterDate(year);

    const easterMonday = new Date(easter);
    easterMonday.setDate(easterMonday.getDate() + 1);

    const ascension = new Date(easter);
    ascension.setDate(ascension.getDate() + 39);

    const whitMonday = new Date(easter);
    whitMonday.setDate(whitMonday.getDate() + 50);

    return [
      new Date(year, 0, 1),   // New Year
      easterMonday,
      new Date(year, 4, 1),   // Labour Day
      new Date(year, 4, 8),   // Victory Day
      ascension,
      whitMonday,
      new Date(year, 6, 14),  // Bastille Day
      new Date(year, 7, 15),  // Assumption
      new Date(year, 10, 1),  // All Saints
      new Date(year, 10, 11), // Armistice
      new Date(year, 11, 25)  // Christmas
    ];
  }

  private computeEasterDate(year: number): Date {
    const a = year % 19;
    const b = Math.floor(year / 100);
    const c = year % 100;
    const d = Math.floor(b / 4);
    const e = b % 4;
    const f = Math.floor((b + 8) / 25);
    const g = Math.floor((b - f + 1) / 3);
    const h = (19 * a + b - d - g + 15) % 30;
    const i = Math.floor(c / 4);
    const k = c % 4;
    const l = (32 + 2 * e + 2 * i - h - k) % 7;
    const m = Math.floor((a + 11 * h + 22 * l) / 451);
    const month = Math.floor((h + l - 7 * m + 114) / 31) - 1;
    const day = ((h + l - 7 * m + 114) % 31) + 1;
    return new Date(year, month, day);
  }
}
