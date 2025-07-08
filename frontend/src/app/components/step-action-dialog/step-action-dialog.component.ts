import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';

import { InterviewStepService } from '../../services/interview-step/interview-step.service';
import { ProjectCardDto, StepStatus } from '../../models/interview-step.models';

export interface StepActionDialogData {
  projectCard: ProjectCardDto;
  stepId: number;
  action: 'waiting_feedback' | 'validated' | 'failed' | 'canceled';
  title: string;
  message: string;
  confirmButtonText: string;
  confirmButtonColor: 'primary' | 'warn';
}

@Component({
  selector: 'app-step-action-dialog',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule
  ],
  templateUrl: './step-action-dialog.component.html',
  styleUrls: ['./step-action-dialog.component.scss']
})
export class StepActionDialogComponent {
  actionForm: FormGroup;
  isSubmitting = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly dialogRef: MatDialogRef<StepActionDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: StepActionDialogData,
    private readonly interviewStepService: InterviewStepService,
    private readonly snackBar: MatSnackBar
  ) {
    this.actionForm = this.fb.group({
      notes: ['']
    });
  }

  onConfirm(): void {
    if (!this.isSubmitting) {
      this.isSubmitting = true;
      
      let actionObservable;
      
      switch (this.data.action) {
        case 'waiting_feedback':
          actionObservable = this.interviewStepService.markAsWaitingFeedback(this.data.stepId);
          break;
        case 'validated':
          actionObservable = this.interviewStepService.markAsValidated(this.data.stepId);
          break;
        case 'failed':
          actionObservable = this.interviewStepService.markAsFailed(this.data.stepId);
          break;
        case 'canceled':
          actionObservable = this.interviewStepService.markAsCanceled(this.data.stepId);
          break;
        default:
          this.isSubmitting = false;
          return;
      }

      actionObservable.subscribe({
        next: (updatedStep) => {
          this.snackBar.open(this.getSuccessMessage(), 'Fermer', { duration: 3000 });
          this.dialogRef.close(updatedStep);
        },
        error: (error) => {
          console.error('Error updating step status:', error);
          this.snackBar.open('Erreur lors de la mise à jour', 'Fermer', { duration: 3000 });
          this.isSubmitting = false;
        }
      });
    }
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  private getSuccessMessage(): string {
    switch (this.data.action) {
      case 'waiting_feedback':
        return 'Étape marquée en attente de retour';
      case 'validated':
        return 'Étape validée avec succès';
      case 'failed':
        return 'Étape marquée comme échouée';
      case 'canceled':
        return 'Étape annulée';
      default:
        return 'Statut mis à jour';
    }
  }

  getActionIcon(): string {
    switch (this.data.action) {
      case 'waiting_feedback':
        return 'hourglass_empty';
      case 'validated':
        return 'check_circle';
      case 'failed':
        return 'cancel';
      case 'canceled':
        return 'block';
      default:
        return 'info';
    }
  }

  getActionColor(): string {
    switch (this.data.action) {
      case 'validated':
        return '#66bb6a';
      case 'failed':
      case 'canceled':
        return '#ef5350';
      case 'waiting_feedback':
        return '#ffee58';
      default:
        return '#2196f3';
    }
  }
}
