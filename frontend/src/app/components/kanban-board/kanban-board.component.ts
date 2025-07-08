import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDividerModule } from '@angular/material/divider';
import { MatDialog } from '@angular/material/dialog';
import { DragDropModule, CdkDragDrop, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { Subject, takeUntil } from 'rxjs';

import { InterviewStepService } from '../../services/interview-step/interview-step.service';
import { AuthService } from '../../services/auth/auth.service';
import {
  KanbanBoardDto,
  ProjectCardDto,
  StepTransitionDto,
  StepStatus,
  INTERVIEW_STEPS_ORDER,
  STEP_STATUS_LABELS,
  STEP_STATUS_COLORS
} from '../../models/interview-step.models';
import { StepScheduleDialogComponent } from '../step-schedule-dialog/step-schedule-dialog.component';
import { StepActionDialogComponent } from '../step-action-dialog/step-action-dialog.component';

@Component({
  selector: 'app-kanban-board',
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatChipsModule,
    MatProgressBarModule,
    MatSnackBarModule,
    MatMenuModule,
    MatTooltipModule,
    MatDividerModule,
    DragDropModule
  ],
  templateUrl: './kanban-board.component.html',
  styleUrls: ['./kanban-board.component.scss']
})
export class KanbanBoardComponent implements OnInit, OnDestroy {
  kanbanBoard: KanbanBoardDto | null = null;
  isLoading = false;
  currentUserId: number | null = null;
  
  private destroy$ = new Subject<void>();

  // Expose constants to template
  readonly STEP_STATUS_LABELS = STEP_STATUS_LABELS;
  readonly STEP_STATUS_COLORS = STEP_STATUS_COLORS;

  constructor(
    private readonly interviewStepService: InterviewStepService,
    private readonly authService: AuthService,
    private readonly snackBar: MatSnackBar,
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
    if (!this.currentUserId) {
      return;
    }

    this.isLoading = true;
    this.interviewStepService.getKanbanBoard(this.currentUserId)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (board) => {
          this.kanbanBoard = board;
          this.isLoading = false;
        },
        error: (_error) => {
          this.snackBar.open('Erreur lors du chargement du tableau', 'Fermer', { duration: 3000 });
          this.isLoading = false;
        }
      });
  }

  onCardDrop(event: CdkDragDrop<ProjectCardDto[]>): void {
    if (event.previousContainer === event.container) {
      // Same column - just reorder
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
    } else {
      // Different column - move project to new step
      const projectCard = event.previousContainer.data[event.previousIndex];
      const fromStepTitle = this.getStepTitleFromContainer(event.previousContainer.id);
      const toStepTitle = this.getStepTitleFromContainer(event.container.id);

      if (fromStepTitle && toStepTitle && fromStepTitle !== toStepTitle) {
        this.transitionProject(projectCard, fromStepTitle, toStepTitle);
      }

      // Update UI immediately for better UX
      transferArrayItem(
        event.previousContainer.data,
        event.container.data,
        event.previousIndex,
        event.currentIndex
      );
    }
  }

  private getStepTitleFromContainer(containerId: string): string | null {
    // Container ID format: "step-column-{stepTitle}"
    const prefix = 'step-column-';
    if (containerId.startsWith(prefix)) {
      return containerId.substring(prefix.length);
    }
    return null;
  }

  private transitionProject(projectCard: ProjectCardDto, fromStepTitle: string, toStepTitle: string): void {
    const transition: StepTransitionDto = {
      projectId: projectCard.projectId,
      fromStepTitle,
      toStepTitle
    };

    this.interviewStepService.transitionProjectToNextStep(transition)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: () => {
          this.snackBar.open(
            `Projet "${projectCard.role}" déplacé vers "${toStepTitle}"`, 
            'Fermer', 
            { duration: 3000 }
          );
          // Reload board to get updated data
          this.loadKanbanBoard();
        },
        error: (_error) => {
          this.snackBar.open('Erreur lors du déplacement du projet', 'Fermer', { duration: 3000 });
          // Reload board to revert UI changes
          this.loadKanbanBoard();
        }
      });
  }

  getStepColumns(): string[] {
    return this.kanbanBoard?.stepOrder || [...INTERVIEW_STEPS_ORDER];
  }

  isBoardEmpty(): boolean {
    return !!this.kanbanBoard && !this.isLoading &&
           this.getStepColumns().every(step => this.getProjectsForStep(step).length === 0);
  }

  getProjectsForStep(stepTitle: string): ProjectCardDto[] {
    return this.kanbanBoard?.columns[stepTitle] || [];
  }

  getStepColumnId(stepTitle: string): string {
    return `step-column-${stepTitle}`;
  }

  getConnectedDropLists(): string[] {
    return this.getStepColumns().map(step => this.getStepColumnId(step));
  }

  getProgressPercentage(card: ProjectCardDto): number {
    if (card.totalSteps === 0) return 0;
    return Math.round((card.completedSteps / card.totalSteps) * 100);
  }

  getStatusChipColor(status: string): string {
    return STEP_STATUS_COLORS[status as StepStatus] || '#bdbdbd';
  }

  getStatusLabel(status: string): string {
    return STEP_STATUS_LABELS[status as StepStatus] || status;
  }

  onScheduleStep(card: ProjectCardDto): void {
    // For now, we'll need to find the step ID - this would be better handled with proper data structure
    // TODO: Improve data structure to include step IDs in project cards
    const stepId = 1; // Placeholder - would need to be retrieved from the card or API

    const dialogRef = this.dialog.open(StepScheduleDialogComponent, {
      width: '500px',
      data: {
        projectCard: card,
        stepId: stepId
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.loadKanbanBoard(); // Refresh the board
      }
    });
  }

  onMarkAsWaitingFeedback(card: ProjectCardDto): void {
    this.openActionDialog(card, 'waiting_feedback', {
      title: 'Marquer en attente de retour',
      message: 'Cette étape sera marquée comme en attente de retour du client ou de l\'équipe.',
      confirmButtonText: 'Marquer en attente',
      confirmButtonColor: 'primary'
    });
  }

  onMarkAsValidated(card: ProjectCardDto): void {
    this.openActionDialog(card, 'validated', {
      title: 'Valider l\'étape',
      message: 'Cette étape sera marquée comme validée et vous pourrez passer à l\'étape suivante.',
      confirmButtonText: 'Valider',
      confirmButtonColor: 'primary'
    });
  }

  onMarkAsFailed(card: ProjectCardDto): void {
    this.openActionDialog(card, 'failed', {
      title: 'Marquer comme échoué',
      message: 'Cette étape sera marquée comme échouée. Cette action peut affecter la progression du projet.',
      confirmButtonText: 'Marquer échoué',
      confirmButtonColor: 'warn'
    });
  }

  onMarkAsCanceled(card: ProjectCardDto): void {
    this.openActionDialog(card, 'canceled', {
      title: 'Annuler l\'étape',
      message: 'Cette étape sera annulée. Vous pourrez la réactiver plus tard si nécessaire.',
      confirmButtonText: 'Annuler',
      confirmButtonColor: 'warn'
    });
  }

  private openActionDialog(card: ProjectCardDto, action: 'waiting_feedback' | 'validated' | 'failed' | 'canceled', config: any): void {
    // For now, we'll need to find the step ID - this would be better handled with proper data structure
    const stepId = 1; // Placeholder - would need to be retrieved from the card or API

    const dialogRef = this.dialog.open(StepActionDialogComponent, {
      width: '500px',
      data: {
        projectCard: card,
        stepId: stepId,
        action: action,
        title: config.title,
        message: config.message,
        confirmButtonText: config.confirmButtonText,
        confirmButtonColor: config.confirmButtonColor
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.loadKanbanBoard(); // Refresh the board
      }
    });
  }

  formatDate(dateString?: string): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString('fr-FR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
      minimumFractionDigits: 0
    }).format(amount);
  }
}
