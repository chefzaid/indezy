import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DragDropModule, CdkDragDrop, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { Subject, takeUntil } from 'rxjs';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ProjectService } from '../../services/project/project.service';
import { AuthService } from '../../services/auth/auth.service';
import {
  KanbanBoardDto,
  KanbanProjectCardDto,
  ProjectStatus,
  PROJECT_STATUS_LABELS,
  PROJECT_STATUS_COLORS,
  PROJECT_STATUS_ICONS
} from '../../models/project.models';

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

  private readonly destroy$ = new Subject<void>();

  readonly STATUS_LABELS = PROJECT_STATUS_LABELS;
  readonly STATUS_COLORS = PROJECT_STATUS_COLORS;
  readonly STATUS_ICONS = PROJECT_STATUS_ICONS;

  constructor(
    private readonly projectService: ProjectService,
    private readonly authService: AuthService,
    private readonly snackBar: MatSnackBar,
    private readonly translate: TranslateService
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
    if (!this.currentUserId) return;

    this.isLoading = true;
    this.projectService.getKanbanBoard(this.currentUserId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (board) => {
          this.kanbanBoard = board;
          this.isLoading = false;
        },
        error: () => {
          this.snackBar.open(this.translate.instant('errors.loadingBoard'), this.translate.instant('common.close'), { duration: 3000 });
          this.isLoading = false;
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

        this.projectService.updateStatus(card.projectId, newStatus as ProjectStatus)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: () => {
              this.snackBar.open(
                `"${card.role}" → ${this.STATUS_LABELS[newStatus as ProjectStatus]}`,
                'OK', { duration: 2000 }
              );
            },
            error: () => {
              this.snackBar.open(this.translate.instant('errors.movingCard'), this.translate.instant('common.close'), { duration: 3000 });
              this.loadKanbanBoard();
            }
          });
      }
    }
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
    if (!rating) return [];
    return new Array(rating).fill('star');
  }
}
