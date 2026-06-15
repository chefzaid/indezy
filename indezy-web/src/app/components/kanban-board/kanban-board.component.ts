import { Component, OnInit, OnDestroy } from '@angular/core';

import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { DragDropModule, CdkDragDrop, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule } from '@ngx-translate/core';

import { ProjectService } from '../../services/project/project.service';
import { AuthService } from '../../services/auth/auth.service';
import { NotificationService } from '../../services/notification/notification.service';
import {
  KanbanQuickAddDialogComponent,
  KanbanQuickAddDialogData
} from '../kanban-quick-add-dialog/kanban-quick-add-dialog.component';
import {
  KanbanLostReasonDialogComponent,
  KanbanLostReasonDialogData
} from '../kanban-lost-reason-dialog/kanban-lost-reason-dialog.component';
import {
  KanbanBoardDto,
  KanbanProjectCardDto,
  ProjectStatus,
  LostReason,
  PROJECT_STATUS_LABELS,
  PROJECT_STATUS_COLORS,
  PROJECT_STATUS_ICONS
} from '../../models/project.models';

@Component({
  selector: 'app-kanban-board',
  imports: [
    RouterModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatDialogModule,
    DragDropModule,
    TranslateModule
],
  templateUrl: './kanban-board.component.html',
  styleUrls: ['./kanban-board.component.scss']
})
export class KanbanBoardComponent implements OnInit, OnDestroy {
  kanbanBoard: KanbanBoardDto | null = null;
  isLoading = false;
  currentUserId: number | null = null;

  /** A card with no activity beyond this many days is flagged as stale. */
  static readonly STALE_THRESHOLD_DAYS = 14;

  private readonly destroy$ = new Subject<void>();

  readonly STATUS_LABELS = PROJECT_STATUS_LABELS;
  readonly STATUS_COLORS = PROJECT_STATUS_COLORS;
  readonly STATUS_ICONS = PROJECT_STATUS_ICONS;

  constructor(
    private readonly projectService: ProjectService,
    private readonly authService: AuthService,
    private readonly notificationService: NotificationService,
    private readonly dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.authService.currentUser$.pipe(
      takeUntil(this.destroy$)
    ).subscribe(user => {
      if (user?.id) {
        this.currentUserId = user.id;
        this.loadKanbanBoard();
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadKanbanBoard(): void {
    if (!this.currentUserId) {return;}

    this.isLoading = true;
    this.projectService.getKanbanBoard(this.currentUserId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (board) => {
          this.kanbanBoard = board;
          this.isLoading = false;
        },
        error: () => {
          this.notificationService.error('errors.loadingBoard');
          this.isLoading = false;
        }
      });
  }

  openQuickAdd(status: string): void {
    if (!this.currentUserId) {return;}

    const dialogData: KanbanQuickAddDialogData = {
      status: status as ProjectStatus,
      statusLabel: this.getColumnLabel(status),
      freelanceId: this.currentUserId
    };

    this.dialog.open(KanbanQuickAddDialogComponent, { data: dialogData })
      .afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe(created => {
        if (created) {
          this.loadKanbanBoard();
        }
      });
  }

  onCardDrop(event: CdkDragDrop<KanbanProjectCardDto[]>): void {
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
    } else {
      const card = event.previousContainer.data[event.previousIndex];
      const newStatus = this.getStatusFromContainerId(event.container.id);

      if (newStatus) {
        // Optimistic UI update
        transferArrayItem(
          event.previousContainer.data,
          event.container.data,
          event.previousIndex,
          event.currentIndex
        );

        if (newStatus === ProjectStatus.LOST) {
          this.promptLostReasonAndPersist(card);
        } else {
          this.persistStatusChange(card, newStatus as ProjectStatus);
        }
      }
    }
  }

  /** Ask why the opportunity was lost before persisting; reverts the move if cancelled. */
  private promptLostReasonAndPersist(card: KanbanProjectCardDto): void {
    const dialogData: KanbanLostReasonDialogData = { role: card.role };
    this.dialog.open(KanbanLostReasonDialogComponent, { data: dialogData })
      .afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((reason?: LostReason) => {
        if (reason) {
          this.persistStatusChange(card, ProjectStatus.LOST, reason);
        } else {
          this.loadKanbanBoard();
        }
      });
  }

  private persistStatusChange(card: KanbanProjectCardDto, newStatus: ProjectStatus, lostReason?: LostReason): void {
    this.projectService.updateStatus(card.projectId, newStatus, lostReason)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          card.status = newStatus;
          card.lostReason = newStatus === ProjectStatus.LOST ? lostReason : undefined;
          this.notificationService.successText(
            `"${card.role}" → ${this.STATUS_LABELS[newStatus]}`,
            2000
          );
        },
        error: () => {
          this.notificationService.error('errors.movingCard');
          this.loadKanbanBoard();
        }
      });
  }

  private getStatusFromContainerId(id: string): string | null {
    const prefix = 'kanban-col-';
    return id.startsWith(prefix) ? id.substring(prefix.length) : null;
  }

  getColumns(): string[] {
    return this.kanbanBoard?.columnOrder || [];
  }

  getCardsForColumn(status: string): KanbanProjectCardDto[] {
    return this.kanbanBoard?.columns[status] || [];
  }

  getColumnId(status: string): string {
    return `kanban-col-${status}`;
  }

  getConnectedDropLists(): string[] {
    return this.getColumns().map(s => this.getColumnId(s));
  }

  getColumnLabel(status: string): string {
    return this.STATUS_LABELS[status as ProjectStatus] || status;
  }

  getColumnColor(status: string): string {
    return this.STATUS_COLORS[status as ProjectStatus] || '#bdbdbd';
  }

  getColumnIcon(status: string): string {
    return this.STATUS_ICONS[status as ProjectStatus] || 'folder';
  }

  getColumnCount(status: string): number {
    return this.getCardsForColumn(status).length;
  }

  isBoardEmpty(): boolean {
    return !!this.kanbanBoard && !this.isLoading &&
      this.getColumns().every(s => this.getCardsForColumn(s).length === 0);
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency', currency: 'EUR', minimumFractionDigits: 0
    }).format(amount);
  }

  getRatingStars(rating?: number): string[] {
    if (!rating) {return [];}
    return new Array(rating).fill('star');
  }

  /** Whole days elapsed since the card's last activity, or null when unknown. */
  getDaysSinceActivity(card: KanbanProjectCardDto): number | null {
    if (!card.updatedAt) {return null;}
    const updated = new Date(card.updatedAt).getTime();
    if (Number.isNaN(updated)) {return null;}
    const diffMs = Date.now() - updated;
    return Math.max(0, Math.floor(diffMs / (1000 * 60 * 60 * 24)));
  }

  /** True when the card has had no activity past the stale threshold. */
  isStale(card: KanbanProjectCardDto): boolean {
    const days = this.getDaysSinceActivity(card);
    return days !== null && days >= KanbanBoardComponent.STALE_THRESHOLD_DAYS;
  }

  toggleFavorite(card: KanbanProjectCardDto, event: Event): void {
    // The card itself is a router link; don't navigate when toggling the star.
    event.stopPropagation();
    event.preventDefault();

    this.projectService.toggleFavorite(card.projectId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (updated) => {
          card.isFavorite = updated.isFavorite;
          this.sortColumn(card.status);
        },
        error: () => this.notificationService.error('errors.updatingFavorite')
      });
  }

  /** Re-pin favorites to the top of a column after a toggle (mirrors the backend ordering). */
  private sortColumn(status: string): void {
    this.getCardsForColumn(status).sort(
      (a, b) => Number(b.isFavorite ?? false) - Number(a.isFavorite ?? false)
    );
  }
}
