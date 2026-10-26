import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
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
import { KanbanLostReasonDialogComponent } from '../kanban-lost-reason-dialog/kanban-lost-reason-dialog.component';

/**
 * The Kanban board's selection: how many cards are picked, and what to do with all of them at once (move them to a
 * column, asking why when it is Lost; give them a source; archive them).
 */
@Component({
  selector: 'app-kanban-selection-bar',
  imports: [MatButtonModule, MatIconModule, MatMenuModule, TranslateModule],
  templateUrl: './kanban-selection-bar.component.html',
  styleUrls: ['./kanban-selection-bar.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class KanbanSelectionBarComponent implements OnInit {
  @Input({ required: true }) projectIds: number[] = [];
  /** The cards were changed: the board reloads and clears its selection. */
  @Output() readonly done = new EventEmitter<void>();
  @Output() readonly clear = new EventEmitter<void>();

  readonly statuses = Object.values(ProjectStatus);
  readonly statusColors = PROJECT_STATUS_COLORS;
  sources: SourceDto[] = [];
  busy = false;

  constructor(
    private readonly projectService: ProjectService,
    private readonly sourceService: SourceService,
    private readonly authService: AuthService,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService,
    private readonly dialog: MatDialog
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

  private apply(change: Omit<ProjectBulkUpdate, 'projectIds'>): void {
    if (this.busy || this.projectIds.length === 0) {
      return;
    }
    this.busy = true;
    this.projectService.bulkUpdate({ ...change, projectIds: this.projectIds }).subscribe({
      next: ({ updated }) => {
        this.busy = false;
        this.notificationService.successText(this.translate.instant('kanban.selection.done', { count: updated }), 3000);
        this.done.emit();
      },
      error: () => {
        this.busy = false;
        this.notificationService.error('kanban.selection.error');
      }
    });
  }
}
