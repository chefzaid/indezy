import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
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
  QuickAddCardDialogComponent,
  QuickAddCardDialogData
} from '../quick-add-card-dialog/quick-add-card-dialog.component';
import {
  KanbanBoardDto,
  KanbanProjectCardDto,
  ProjectStatus,
  LostReason,
  PROJECT_STATUS_LABELS,
  PROJECT_STATUS_COLORS,
  PROJECT_STATUS_ICONS
} from '../../models/project.models';
import {
  LostReasonDialogComponent,
  LostReasonDialogData
} from '../lost-reason-dialog/lost-reason-dialog.component';

@Component({
  selector: 'app-kanban-board',
  imports: [
    CommonModule,
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
  showFavoritesOnly = false;

  private readonly destroy$ = new Subject<void>();

  readonly STATUS_LABELS = PROJECT_STATUS_LABELS;
  readonly STATUS_COLORS = PROJECT_STATUS_COLORS;
  readonly STATUS_ICONS = PROJECT_STATUS_ICONS;

  // Card aging thresholds (days without activity) for highlighting stale opportunities.
  readonly AGING_WARN_DAYS = 7;
  readonly AGING_STALE_DAYS = 14;

  // Closed columns are excluded from aging — a won/lost deal is not "stale".
  private readonly AGING_EXCLUDED_STATUSES: ReadonlySet<string> = new Set([
    ProjectStatus.WON,
    ProjectStatus.LOST
  ]);

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

  onCardDrop(event: CdkDragDrop<KanbanProjectCardDto[]>): void {
    if (event.previousContainer === event.container) {
      if (event.previousIndex === event.currentIndex) {
        return;
      }
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
      this.persistColumnOrder(event.container.data);
    } else {
      const card = event.previousContainer.data[event.previousIndex];
      const newStatus = this.getStatusFromContainerId(event.container.id);

      if (!newStatus) {
        return;
      }

      // Moving to LOST: capture the reason first; the card stays put until confirmed.
      if (newStatus === ProjectStatus.LOST) {
        this.promptLostReason(card);
        return;
      }

      // Optimistic UI update
      transferArrayItem(
        event.previousContainer.data,
        event.container.data,
        event.previousIndex,
        event.currentIndex
      );

      this.projectService.updateStatus(card.projectId, newStatus as ProjectStatus)
        .pipe(takeUntil(this.destroy$))
        .subscribe({
          next: () => {
            this.notificationService.successText(
              `"${card.role}" → ${this.STATUS_LABELS[newStatus as ProjectStatus]}`,
              2000
            );
          },
          error: () => {
            this.notificationService.error('errors.movingCard');
            this.loadKanbanBoard();
          }
        });
    }
  }

  private promptLostReason(card: KanbanProjectCardDto): void {
    const data: LostReasonDialogData = { role: card.role };
    this.dialog.open(LostReasonDialogComponent, { data, width: '380px' })
      .afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((result?: { reason?: LostReason }) => {
        if (!result) {
          return; // cancelled: card snaps back to its column
        }
        this.projectService.markAsLost(card.projectId, result.reason)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: () => {
              this.notificationService.successText(
                `"${card.role}" → ${this.STATUS_LABELS[ProjectStatus.LOST]}`,
                2000
              );
              this.loadKanbanBoard();
            },
            error: () => this.notificationService.error('errors.movingCard')
          });
      });
  }

  openQuickAdd(status: string): void {
    if (!this.currentUserId) {return;}

    const data: QuickAddCardDialogData = {
      freelanceId: this.currentUserId,
      status: status as ProjectStatus
    };

    this.dialog.open(QuickAddCardDialogComponent, { data, width: '420px' })
      .afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe(created => {
        if (created) {
          this.loadKanbanBoard();
        }
      });
  }

  private persistColumnOrder(cards: KanbanProjectCardDto[]): void {
    const orderedIds = cards.map(card => card.projectId);
    this.projectService.reorderColumn(orderedIds)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        error: () => {
          this.notificationService.error('kanban.reorder.error');
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
    const cards = this.kanbanBoard?.columns[status] || [];
    return this.showFavoritesOnly ? cards.filter(c => c.favorite) : cards;
  }

  toggleFavoritesFilter(): void {
    this.showFavoritesOnly = !this.showFavoritesOnly;
  }

  toggleFavorite(card: KanbanProjectCardDto, event: Event): void {
    // Prevent the card's routerLink from firing when the star is clicked.
    event.stopPropagation();
    event.preventDefault();

    const newValue = !card.favorite;
    this.projectService.setFavorite(card.projectId, newValue)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          card.favorite = newValue;
          // Reload so the backend re-pins favorites to the top of each column.
          this.loadKanbanBoard();
        },
        error: () => this.notificationService.error('kanban.favorite.error')
      });
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

  /**
   * Whole days elapsed since the card's last activity (its `updatedAt` timestamp).
   * Returns null when no timestamp is available or it cannot be parsed.
   */
  getDaysSinceActivity(card: KanbanProjectCardDto): number | null {
    if (!card.updatedAt) {return null;}
    const updated = new Date(card.updatedAt).getTime();
    if (Number.isNaN(updated)) {return null;}
    const diffMs = Date.now() - updated;
    if (diffMs < 0) {return 0;}
    return Math.floor(diffMs / (1000 * 60 * 60 * 24));
  }

  /**
   * Aging severity for a card: 'none', 'warn' (>= AGING_WARN_DAYS) or 'stale' (>= AGING_STALE_DAYS).
   * Won/Lost cards never age.
   */
  getAgingLevel(card: KanbanProjectCardDto): 'none' | 'warn' | 'stale' {
    if (this.AGING_EXCLUDED_STATUSES.has(card.status)) {return 'none';}
    const days = this.getDaysSinceActivity(card);
    if (days === null) {return 'none';}
    if (days >= this.AGING_STALE_DAYS) {return 'stale';}
    if (days >= this.AGING_WARN_DAYS) {return 'warn';}
    return 'none';
  }

  isAging(card: KanbanProjectCardDto): boolean {
    return this.getAgingLevel(card) !== 'none';
  }
}
