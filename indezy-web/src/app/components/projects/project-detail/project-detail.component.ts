import { Component, OnInit, OnDestroy, ChangeDetectionStrategy, LOCALE_ID, inject } from '@angular/core';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatMenuModule } from '@angular/material/menu';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ProjectService } from '../../../services/project/project.service';
import {
  InterviewStepDto,
  LostReason,
  PROJECT_STATUS_COLORS,
  PROJECT_STATUS_ICONS,
  ProjectDto,
  ProjectNote,
  ProjectStatus,
  STEP_STATUS_COLORS,
  StepStatus
} from '../../../models';
import { InterviewStepService } from '../../../services/interview-step/interview-step.service';
import { fromIsoDate } from '../../../shared/locale/app-locale';
import {
  InterviewStepDialogComponent,
  InterviewStepDialogData
} from '../interview-step-dialog/interview-step-dialog.component';
import {
  KanbanLostReasonDialogComponent,
  KanbanLostReasonDialogData
} from '../../kanban-lost-reason-dialog/kanban-lost-reason-dialog.component';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { MarkdownService } from '../../../shared/services/markdown.service';
import { SafeHtml } from '@angular/platform-browser';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { SkillMatchService } from '../../../shared/services/skill-match.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { AuthService } from '../../../services/auth/auth.service';
import { RenameTagDialogComponent, RenameTagResult } from '../rename-tag-dialog/rename-tag-dialog.component';
import { NOTE_TEMPLATES } from './note-templates';

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
        MatSelectModule,
        MatMenuModule,
        MatDialogModule,
        MatTooltipModule,
        TranslateModule
    ],
    templateUrl: './project-detail.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./project-detail.component.scss']
})
export class ProjectDetailComponent implements OnInit, OnDestroy {
  project?: ProjectDto;
  isLoading = false;
  projectId?: number;

  notes: ProjectNote[] = [];
  steps: InterviewStepDto[] = [];
  isUpdatingStatus = false;

  readonly statusOptions = Object.values(ProjectStatus);
  readonly statusColors = PROJECT_STATUS_COLORS;
  readonly statusIcons = PROJECT_STATUS_ICONS;
  readonly stepStatusOptions = Object.values(StepStatus);
  readonly stepStatusColors = STEP_STATUS_COLORS;
  private readonly locale = inject(LOCALE_ID);
  private readonly interviewStepService = inject(InterviewStepService);
  newNoteContent = '';
  isSavingNote = false;
  readonly noteTemplates = NOTE_TEMPLATES;
  selectedNoteTemplate = '';

  private readonly destroy$ = new Subject<void>();

  constructor(
    private readonly projectService: ProjectService,
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService,
    private readonly confirmDialog: ConfirmDialogService,
    private readonly markdownService: MarkdownService,
    private readonly skillMatchService: SkillMatchService,
    private readonly userManagementService: UserManagementService,
    private readonly authService: AuthService,
    private readonly dialog: MatDialog
  ) {}

  userSkills: string[] = [];

  /** The opportunity's tech stack as individual skill tags. */
  getTechTags(): string[] {
    return this.skillMatchService.parseTags(this.project?.techStack);
  }

  /** Match score (0–100) of the opportunity against the freelancer's skill profile. */
  getMatchScore(): number {
    return this.skillMatchService.matchScore(this.project?.techStack, this.userSkills);
  }

  private loadUserSkills(): void {
    this.userManagementService.getUserProfile()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: profile => this.userSkills = profile.skills ?? [],
        error: () => { /* match score simply stays at 0 when skills can't be loaded */ }
      });
  }

  /** Renames one of the opportunity's tags across every project. */
  openRenameTag(): void {
    const tags = this.getTechTags();
    const freelanceId = this.authService.getUser()?.id;
    if (tags.length === 0 || !freelanceId) {
      return;
    }
    this.dialog.open(RenameTagDialogComponent, { data: { tags }, autoFocus: false })
      .afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((result: RenameTagResult | undefined) => {
        if (!result) {
          return;
        }
        this.projectService.renameTag(freelanceId, result.from, result.to)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: count => {
              this.notificationService.success('projects.skills.renameSuccess', 3000, { count });
              this.loadProject();
            },
            error: () => this.notificationService.error('projects.skills.renameError')
          });
      });
  }

  /** Renders note content written in Markdown as safe, bindable HTML. */
  renderMarkdown(text: string): SafeHtml {
    return this.markdownService.render(text);
  }

  ngOnInit(): void {
    this.route.params.pipe(takeUntil(this.destroy$)).subscribe(params => {
      if (params['id']) {
        this.projectId = +params['id'];
        this.loadProject();
      }
    });
    this.loadUserSkills();
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
            this.loadSteps();
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

  private loadSteps(): void {
    if (!this.projectId) { return; }
    this.interviewStepService.getByProjectIdOrderByDate(this.projectId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (steps) => this.steps = steps,
        error: () => this.notificationService.error('steps.loadError')
      });
  }

  /** Moves the opportunity to another pipeline stage, asking why when it is lost. */
  changeStatus(status: ProjectStatus): void {
    if (!this.project?.id || status === this.project.status || this.isUpdatingStatus) { return; }
    if (status !== ProjectStatus.LOST) {
      this.persistStatus(status);
      return;
    }
    const dialogData: KanbanLostReasonDialogData = { role: this.project.role };
    this.dialog.open(KanbanLostReasonDialogComponent, { data: dialogData })
      .afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((reason?: LostReason) => {
        if (reason) {
          this.persistStatus(ProjectStatus.LOST, reason);
        }
      });
  }

  private persistStatus(status: ProjectStatus, lostReason?: LostReason): void {
    const projectId = this.project!.id!;
    this.isUpdatingStatus = true;
    this.projectService.updateStatus(projectId, status, lostReason)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (updated) => {
          this.project = { ...this.project!, status: updated.status, lostReason: updated.lostReason };
          this.isUpdatingStatus = false;
          this.notificationService.success('projects.detail.statusUpdated', 2500, {
            status: this.translate.instant('projects.statuses.' + updated.status)
          });
        },
        error: () => {
          this.isUpdatingStatus = false;
          this.notificationService.error('errors.movingCard');
        }
      });
  }

  openStepDialog(step?: InterviewStepDto): void {
    if (!this.projectId) { return; }
    const data: InterviewStepDialogData = { projectId: this.projectId, step };
    this.dialog.open(InterviewStepDialogComponent, { data, autoFocus: 'first-tabbable' })
      .afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe(saved => {
        if (saved) {
          this.refreshAfterStepChange();
        }
      });
  }

  setStepStatus(step: InterviewStepDto, status: StepStatus): void {
    if (!step.id || step.status === status) { return; }
    this.interviewStepService.updateStatus(step.id, status)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.notificationService.success('steps.statusUpdated', 2000);
          this.refreshAfterStepChange();
        },
        error: () => this.notificationService.error('errors.updatingStep')
      });
  }

  deleteStep(step: InterviewStepDto): void {
    if (!step.id) { return; }
    const stepId = step.id;
    this.confirmDialog.confirm({
      messageKey: 'steps.deleteConfirm',
      messageParams: { title: step.title },
      danger: true
    }).subscribe(confirmed => {
      if (!confirmed) { return; }
      this.interviewStepService.delete(stepId)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.notificationService.success('steps.deletedSuccess', 2000);
            this.refreshAfterStepChange();
          },
          error: () => this.notificationService.error('errors.updatingStep')
        });
    });
  }

  /** Step counters on the project come from the server; reload both after a change. */
  private refreshAfterStepChange(): void {
    this.loadSteps();
    if (!this.projectId) { return; }
    this.projectService.getById(this.projectId)
      .pipe(takeUntil(this.destroy$))
      .subscribe(project => this.project = project);
  }

  isStepDone(step: InterviewStepDto): boolean {
    return step.status === StepStatus.VALIDATED;
  }

  /**
   * Inserts the selected note template into the editor. Appends to any text already
   * typed rather than overwriting it, then clears the template selection.
   */
  applyNoteTemplate(templateId: string): void {
    if (!templateId) { return; }
    const body = this.translate.instant(`projects.notes.templates.${templateId}.body`) as string;
    this.newNoteContent = this.newNoteContent.trim()
      ? `${this.newNoteContent.trimEnd()}\n\n${body}`
      : body;
    this.selectedNoteTemplate = '';
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
    if (!this.projectId) { return; }
    const projectId = this.projectId;

    this.confirmDialog.confirm({
      messageKey: 'projects.notes.deleteConfirm',
      danger: true
    }).subscribe(confirmed => {
      if (!confirmed) { return; }
      this.projectService.deleteProjectNote(projectId, noteId)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => this.notes = this.notes.filter(n => n.id !== noteId),
          error: () => this.notificationService.error('projects.notes.deleteError')
        });
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
    
    const project = this.project;
    this.confirmDialog.confirm({
      messageKey: 'projects.confirmDelete',
      messageParams: { name: project.role },
      danger: true
    }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.projectService.delete(project.id!)
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
    });
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

  getStatusColor(status?: ProjectStatus): string {
    return this.statusColors[status ?? ProjectStatus.CONTACT];
  }

  formatDate(dateString?: string): string {
    const date = fromIsoDate(dateString);
    if (!date) { return this.translate.instant('common.notSpecified'); }
    return formatDate(date, 'mediumDate', this.locale);
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
