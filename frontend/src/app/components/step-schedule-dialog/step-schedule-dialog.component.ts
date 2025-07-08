import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';

import { InterviewStepService } from '../../services/interview-step/interview-step.service';
import { ProjectCardDto } from '../../models/interview-step.models';

export interface StepScheduleDialogData {
  projectCard: ProjectCardDto;
  stepId: number;
}

@Component({
  selector: 'app-step-schedule-dialog',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule
  ],
  templateUrl: './step-schedule-dialog.component.html',
  styleUrls: ['./step-schedule-dialog.component.scss']
})
export class StepScheduleDialogComponent {
  scheduleForm: FormGroup;
  isSubmitting = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly dialogRef: MatDialogRef<StepScheduleDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: StepScheduleDialogData,
    private readonly interviewStepService: InterviewStepService,
    private readonly snackBar: MatSnackBar
  ) {
    this.scheduleForm = this.fb.group({
      date: [null, Validators.required],
      time: ['', Validators.required],
      notes: ['']
    });

    // Pre-fill with existing date if available
    if (this.data.projectCard.currentStepDate) {
      const existingDate = new Date(this.data.projectCard.currentStepDate);
      this.scheduleForm.patchValue({
        date: existingDate,
        time: this.formatTimeForInput(existingDate)
      });
    }
  }

  onSubmit(): void {
    if (this.scheduleForm.valid && !this.isSubmitting) {
      this.isSubmitting = true;
      
      const formValue = this.scheduleForm.value;
      const scheduledDateTime = this.combineDateTime(formValue.date, formValue.time);

      this.interviewStepService.scheduleStep(this.data.stepId, scheduledDateTime)
        .subscribe({
          next: (updatedStep) => {
            this.snackBar.open('Entretien planifié avec succès', 'Fermer', { duration: 3000 });
            this.dialogRef.close(updatedStep);
          },
          error: (error) => {
            console.error('Error scheduling step:', error);
            this.snackBar.open('Erreur lors de la planification', 'Fermer', { duration: 3000 });
            this.isSubmitting = false;
          }
        });
    }
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  private combineDateTime(date: Date, time: string): Date {
    const [hours, minutes] = time.split(':').map(Number);
    const combined = new Date(date);
    combined.setHours(hours, minutes, 0, 0);
    return combined;
  }

  private formatTimeForInput(date: Date): string {
    return date.toTimeString().slice(0, 5); // HH:MM format
  }

  // Date filter to disable past dates
  dateFilter = (date: Date | null): boolean => {
    if (!date) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return date >= today;
  };
}
