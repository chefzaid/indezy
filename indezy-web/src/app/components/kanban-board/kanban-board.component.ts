import { ProjectLinkComponent } from '../../shared/components/project-link/project-link.component';
import { Component, OnInit, OnChanges, OnDestroy, ChangeDetectionStrategy, LOCALE_ID, Input, SimpleChanges, inject } from '@angular/core';

import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { DragDropModule, CdkDragDrop, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ProjectService } from '../../services/project/project.service';
import { AuthService } from '../../services/auth/auth.service';
import { NotificationService } from '../../services/notification/notification.service';
import {
  KanbanQuickAddDialogComponent,
  KanbanQuickAddDialogData
} from '../kanban-quick-add-dialog/kanban-quick-add-dialog.component';
import {
  KanbanRefusalDialogComponent,
  KanbanRefusalDialogData
} from '../kanban-refusal-dialog/kanban-refusal-dialog.component';
import { RefusedByBadgeComponent } from '../../shared/components/refused-by-badge/refused-by-badge.component';
import { ProjectSelectionBarComponent } from '../project-selection-bar/project-selection-bar.component';
import {
  KanbanBoardDto,
  KanbanProjectCardDto,
  NonSolicitation,
  ProjectStatus,
  Refusal,
  PROJECT_STATUS_COLORS,
  PROJECT_STATUS_ICONS
} from '../../models/project.models';
import { EnterOpensDirective } from '../../shared/directives/enter-opens.directive';
import { CommuteService } from '../../services/commute/commute.service';
import { tripTime } from '../../shared/utils/trip-time';

@Component({
  selector: 'app-kanban-board',
  imports: [
        ProjectLinkComponent,
    EnterOpensDirective,
    RouterModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatTooltipModule,
    MatCheckboxModule,
    ProjectSelectionBarComponent,
    RefusedByBadgeComponent,
    DragDropModule,
    TranslateModule
],
  templateUrl: './kanban-board.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./kanban-board.component.scss']
})
export class KanbanBoardComponent implements OnInit, OnChanges, OnDestroy {
  /** Season whose pipeline is shown; null or undefined shows every opportunity. */
  @Input() seasonId: number | null = null;

  kanbanBoard: KanbanBoardDto | null = null;
  isLoading = false;
  currentUserId: number | null = null;
  /** The cards picked for an action on several at once, in the order they were picked. */
  selectedIds: number[] = [];

  private readonly destroy$ = new Subject<void>();
  private readonly locale = inject(LOCALE_ID);
  private readonly translate = inject(TranslateService);
  private readonly commuteService = inject(CommuteService);
  /** Each opportunity's one-way drive at rush hour, in minutes, when one is known, and whether it exceeds the profile's longest. */
  commutes = new Map<number, { minutes: number; beyond: boolean }>();
  /** The opportunities a non-solicitation clause covers, with the clause. */
  clauses = new Map<number, NonSolicitation>();

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
        this.loadCommutes();
        this.loadClauses();
      }
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['seasonId'] && !changes['seasonId'].firstChange) {
      this.loadKanbanBoard();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  loadKanbanBoard(): void {
    if (!this.currentUserId) {return;}

    this.isLoading = true;
    this.selectedIds = [];
    this.projectService.getKanbanBoard(this.currentUserId, this.seasonId)
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

  /** The drive to each opportunity, for its card's chip; a failed lookup leaves the cards without it. */
  loadCommutes(): void {
    this.commuteService.getProjectsSortedByCommute(this.currentUserId!, 'DRIVING').pipe(takeUntil(this.destroy$)).subscribe({
      next: results => {
        this.commutes = new Map(results
          .filter(result => result.project.id !== undefined && !result.commute?.remote)
          .flatMap(result => {
            const seconds = result.commute?.rushDurationInSeconds ?? result.commute?.durationInSeconds;
            return seconds === null || seconds === undefined ? [] :
              [[result.project.id!, { minutes: seconds / 60, beyond: result.commute?.beyondMaximum === true }] as const];
          }));
      },
      error: () => {
        this.commutes = new Map();
      }
    });
  }

  /** The non-solicitation clauses covering cards; a failed read leaves the cards unmarked. */
  loadClauses(): void {
    this.projectService.getNonSolicitations(this.currentUserId!).pipe(takeUntil(this.destroy$)).subscribe({
      next: clauses => {
        this.clauses = new Map(clauses.map(clause => [clause.projectId, clause] as const));
      },
      error: () => {
        this.clauses = new Map();
      }
    });
  }

  /** "≈ 1 h 45": a card's drive at rush hour. */
  commuteTime(minutes: number): string {
    return `≈ ${tripTime(minutes)}`;
  }

  openQuickAdd(status: string): void {
    if (!this.currentUserId) {return;}

    const dialogData: KanbanQuickAddDialogData = {
      status: status as ProjectStatus,
      statusLabel: this.getColumnLabel(status),
      freelanceId: this.currentUserId,
      seasonId: this.seasonId
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
      if (event.previousIndex === event.currentIndex) {return;}
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
      this.persistColumnOrder(event.container.data);
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
          this.promptRefusalAndPersist(card);
        } else {
          this.persistStatusChange(card, newStatus as ProjectStatus);
        }
      }
    }
  }

  /** Asks who refused the opportunity, and why, before persisting; reverts the move if cancelled. */
  private promptRefusalAndPersist(card: KanbanProjectCardDto): void {
    const dialogData: KanbanRefusalDialogData = { role: card.role };
    KanbanRefusalDialogComponent.open(this.dialog, dialogData)
      .afterClosed()
      .pipe(takeUntil(this.destroy$))
      .subscribe((refusal?: Refusal) => {
        if (refusal) {
          this.persistStatusChange(card, ProjectStatus.LOST, refusal);
        } else {
          this.loadKanbanBoard();
        }
      });
  }

  private persistStatusChange(card: KanbanProjectCardDto, newStatus: ProjectStatus, refusal?: Refusal): void {
    this.projectService.updateStatus(card.projectId, newStatus, refusal)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          card.status = newStatus;
          card.refusedBy = refusal?.refusedBy;
          card.lostReason = refusal?.lostReason;
          this.notificationService.successText(
            `"${card.role}" → ${this.getColumnLabel(newStatus)}`,
            2000
          );
        },
        error: () => {
          this.notificationService.error('errors.movingCard');
          this.loadKanbanBoard();
        }
      });
  }

  /** Saves the manual card order of a column after an in-column drag; reloads on failure. */
  private persistColumnOrder(cards: KanbanProjectCardDto[]): void {
    if (!this.currentUserId) {return;}

    const orderedIds = cards.map(card => card.projectId);
    this.projectService.reorderKanbanColumn(this.currentUserId, orderedIds)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
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
    return this.translate.instant('projects.statuses.' + status);
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
    return new Intl.NumberFormat(this.locale, {
      style: 'currency', currency: 'EUR', minimumFractionDigits: 0, maximumFractionDigits: 0
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
    const threshold = this.kanbanBoard?.inactivityDays;
    return days !== null && threshold !== undefined && days >= threshold;
  }

  /** Picks or unpicks a card for an action on several at once. */
  toggleSelected(card: KanbanProjectCardDto): void {
    this.selectedIds = this.selectedIds.includes(card.projectId) ?
      this.selectedIds.filter(id => id !== card.projectId) : [...this.selectedIds, card.projectId];
  }

  clearSelection(): void {
    this.selectedIds = [];
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
