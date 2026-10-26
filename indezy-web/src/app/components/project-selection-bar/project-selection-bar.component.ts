import {
  ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Injector, Input, OnInit, Output, ViewChild, afterNextRender, inject
} from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { LostReason, ProjectBulkUpdate, ProjectStatus, PROJECT_STATUS_COLORS } from '../../models/project.models';
import { SourceDto } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ProjectService } from '../../services/project/project.service';
import { SourceService } from '../../services/source/source.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { KanbanLostReasonDialogComponent } from '../kanban-lost-reason-dialog/kanban-lost-reason-dialog.component';

/**
 * The opportunities picked on the Kanban board or in the list: how many, and what to do with all of them at once (move
 * them to a column, asking why when it is Lost; give them a source; add a tag; archive them; delete them).
 */
@Component({
  selector: 'app-project-selection-bar',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatMenuModule, TranslateModule],
  templateUrl: './project-selection-bar.component.html',
  styleUrls: ['./project-selection-bar.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProjectSelectionBarComponent implements OnInit {
  @Input({ required: true }) projectIds: number[] = [];
  /** The opportunities were changed or deleted: the page reloads and clears its selection. */
  @Output() readonly done = new EventEmitter<void>();
  @Output() readonly clear = new EventEmitter<void>();

  readonly statuses = Object.values(ProjectStatus);
  readonly statusColors = PROJECT_STATUS_COLORS;
  readonly tagMax = 60;
  readonly tag = new FormControl('', { nonNullable: true });
  sources: SourceDto[] = [];
  busy = false;
  tagging = false;

  @ViewChild('tagInput') tagInput?: ElementRef<HTMLInputElement>;
  private readonly injector = inject(Injector);

  constructor(
    private readonly projectService: ProjectService,
    private readonly sourceService: SourceService,
    private readonly authService: AuthService,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService,
    private readonly dialog: MatDialog,
    private readonly confirmDialog: ConfirmDialogService
  ) {}

  ngOnInit(): void {
    const freelanceId = this.authService.getUser()?.id;
    if (freelanceId) {
      this.sourceService.getByFreelanceId(freelanceId).subscribe({
        next: sources => this.sources = [...sources].sort((a, b) => a.name.localeCompare(b.name)),
        error: () => this.sources = []
      });
    }
  }

  moveTo(status: ProjectStatus): void {
    if (status !== ProjectStatus.LOST) {
      this.apply({ action: 'STATUS', status });
      return;
    }
    const role = this.translate.instant('kanban.selection.lostRole', { count: this.projectIds.length });
    KanbanLostReasonDialogComponent.open(this.dialog, { role }).afterClosed().subscribe((lostReason?: LostReason) => {
      if (lostReason) {
        this.apply({ action: 'STATUS', status, lostReason });
      }
    });
  }

  assignSource(sourceId: number): void {
    this.apply({ action: 'SOURCE', sourceId });
  }

  archive(): void {
    this.apply({ action: 'ARCHIVE' });
  }

  startTagging(): void {
    this.tag.setValue('');
    this.tagging = true;
    // The field takes the focus once it is shown.
    afterNextRender(() => this.tagInput?.nativeElement.focus(), { injector: this.injector });
  }

  /** Adds the typed tag (commas cannot be part of one) to each tech stack that lacks it. */
  addTag(): void {
    const tag = this.tag.value.replaceAll(',', ' ').trim();
    if (tag) {
      this.tagging = false;
      this.apply({ action: 'TAG', tag });
    }
  }

  delete(): void {
    this.confirmDialog.confirm({
      messageKey: 'kanban.selection.confirmDelete',
      messageParams: { count: this.projectIds.length },
      confirmKey: 'common.delete',
      danger: true
    }).subscribe(confirmed => {
      if (confirmed) {
        this.apply({ action: 'DELETE' }, 'kanban.selection.deleted');
      }
    });
  }

  private apply(change: Omit<ProjectBulkUpdate, 'projectIds'>, doneKey = 'kanban.selection.done'): void {
    if (this.busy || this.projectIds.length === 0) {
      return;
    }
    this.busy = true;
    this.projectService.bulkUpdate({ ...change, projectIds: this.projectIds }).subscribe({
      next: ({ updated }) => {
        this.busy = false;
        this.notificationService.successText(this.translate.instant(doneKey, { count: updated }), 3000);
        this.done.emit();
      },
      error: () => {
        this.busy = false;
        this.notificationService.error('kanban.selection.error');
      }
    });
  }
}
