import { Component, Inject, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { InterviewStepService } from '../../../services/interview-step/interview-step.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { InterviewStepDto, STEP_STATUS_COLORS, StepStatus } from '../../../models';

export interface InterviewStepDialogData {
  projectId: number;
  /** The step to edit; omitted when adding a new step. */
  step?: InterviewStepDto;
}

/** Default step titles offered as suggestions (translation keys under steps.suggestions). */
export const STEP_TITLE_SUGGESTION_KEYS = [
  'firstContact',
  'commercialInterview',
  'technicalTest',
  'technicalInterview',
  'managerInterview',
  'clientInterview',
  'finalValidation'
];

/**
 * Adds or edits an interview step of a project: title, stage status, optional
 * date and time (sent as local wall-clock time, as the API stores LocalDateTime) and notes.
 */
@Component({
  selector: 'app-interview-step-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatAutocompleteModule,
    MatIconModule,
    TranslateModule
  ],
  templateUrl: './interview-step-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./interview-step-dialog.component.scss']
})
export class InterviewStepDialogComponent {
  readonly statusOptions = Object.values(StepStatus);
  readonly statusColors = STEP_STATUS_COLORS;
  readonly titleSuggestionKeys = STEP_TITLE_SUGGESTION_KEYS;
  readonly isEditMode: boolean;
  stepForm: FormGroup;
  isSubmitting = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly dialogRef: MatDialogRef<InterviewStepDialogComponent, InterviewStepDto>,
    @Inject(MAT_DIALOG_DATA) public data: InterviewStepDialogData,
    private readonly interviewStepService: InterviewStepService,
    private readonly notificationService: NotificationService
  ) {
    this.isEditMode = !!data.step?.id;
    const existingDate = data.step?.date ? new Date(data.step.date) : null;
    this.stepForm = this.fb.group({
      title: [data.step?.title ?? '', [Validators.required, Validators.maxLength(255)]],
      status: [data.step?.status ?? StepStatus.TO_PLAN, Validators.required],
      date: [existingDate],
      time: [existingDate ? InterviewStepDialogComponent.toTimeInput(existingDate) : ''],
      notes: [data.step?.notes ?? '']
    });

    // Picking a date for a step that is still "to plan" means it is now planned.
    this.stepForm.get('date')?.valueChanges.subscribe(date => {
      if (date && this.stepForm.get('status')?.value === StepStatus.TO_PLAN) {
        this.stepForm.get('status')?.setValue(StepStatus.PLANNED);
      }
    });
  }

  onSubmit(): void {
    if (this.stepForm.invalid || this.isSubmitting) {
      this.stepForm.markAllAsTouched();
      return;
    }
    this.isSubmitting = true;
    const value = this.stepForm.value;
    const payload = {
      title: value.title.trim(),
      status: value.status as StepStatus,
      notes: value.notes?.trim() || undefined,
      date: InterviewStepDialogComponent.toLocalDateTime(value.date, value.time),
      projectId: this.data.projectId
    };

    const request = this.isEditMode
      ? this.interviewStepService.update(this.data.step!.id!, { ...payload, id: this.data.step!.id! })
      : this.interviewStepService.create(payload);

    request.subscribe({
      next: saved => {
        this.notificationService.success(this.isEditMode ? 'steps.updatedSuccess' : 'steps.addedSuccess');
        this.dialogRef.close(saved);
      },
      error: () => {
        this.notificationService.error('errors.updatingStep');
        this.isSubmitting = false;
      }
    });
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  /** Serializes a date and optional HH:mm time as an ISO local date-time without a zone. */
  static toLocalDateTime(date: Date | null, time: string | null): string | undefined {
    if (!date) {
      return undefined;
    }
    const [hours, minutes] = (time || '09:00').split(':').map(Number);
    const pad = (n: number): string => `${n}`.padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
      `T${pad(hours || 0)}:${pad(minutes || 0)}:00`;
  }

  static toTimeInput(date: Date): string {
    return `${date.getHours()}`.padStart(2, '0') + ':' + `${date.getMinutes()}`.padStart(2, '0');
  }
}
