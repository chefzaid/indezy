import { Component, Inject, ChangeDetectionStrategy } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { InterviewStepService } from '../../../services/interview-step/interview-step.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { InterviewStepDto, STEP_STATUS_COLORS, StepChecklistItem, StepStatus } from '../../../models';
import { toIsoDate } from '../../../shared/locale/app-locale';

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

/** Preparation items offered on any step (translation keys under steps.checklist.suggestions). */
export const CHECKLIST_SUGGESTION_KEYS = ['jobDescription', 'company', 'examples', 'questions', 'rate', 'connection'];

/** The longest checklist item the API keeps. */
export const CHECKLIST_LABEL_MAX = 200;

/**
 * Adds or edits an interview step of a project: title, stage status, optional
 * date and time (sent as local wall-clock time, as the API stores LocalDateTime), notes and a preparation checklist.
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
    MatCheckboxModule,
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
  readonly labelMax = CHECKLIST_LABEL_MAX;
  stepForm: FormGroup;
  /** The item being typed, added to the checklist with Enter or the add button. */
  readonly newItem = new FormControl('', { nonNullable: true });
  isSubmitting = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly dialogRef: MatDialogRef<InterviewStepDialogComponent, InterviewStepDto>,
    @Inject(MAT_DIALOG_DATA) public data: InterviewStepDialogData,
    private readonly interviewStepService: InterviewStepService,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService
  ) {
    this.isEditMode = !!data.step?.id;
    const existingDate = data.step?.date ? new Date(data.step.date) : null;
    this.stepForm = this.fb.group({
      title: [data.step?.title ?? '', [Validators.required, Validators.maxLength(255)]],
      status: [data.step?.status ?? StepStatus.TO_PLAN, Validators.required],
      date: [existingDate],
      time: [existingDate ? InterviewStepDialogComponent.toTimeInput(existingDate) : ''],
      notes: [data.step?.notes ?? ''],
      checklist: this.fb.array((data.step?.checklist ?? []).map(item => this.checklistItem(item)))
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
      checklist: (value.checklist as StepChecklistItem[]).map(item => ({ label: item.label.trim(), done: item.done })),
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

  get checklist(): FormArray<FormGroup> {
    return this.stepForm.get('checklist') as FormArray<FormGroup>;
  }

  /** Adds the typed item, or {@code label}, unless it is empty or already listed. */
  addItem(label: string = this.newItem.value): void {
    const trimmed = label.trim().slice(0, CHECKLIST_LABEL_MAX);
    if (!trimmed || this.listed(trimmed)) {
      return;
    }
    this.checklist.push(this.checklistItem({ label: trimmed, done: false }));
    if (label === this.newItem.value) {
      this.newItem.reset();
    }
  }

  removeItem(index: number): void {
    this.checklist.removeAt(index);
  }

  /** Adds the suggested preparation items that are not listed yet, in the interface language. */
  addSuggestions(): void {
    for (const key of CHECKLIST_SUGGESTION_KEYS) {
      this.addItem(this.translate.instant(`steps.checklist.suggestions.${key}`));
    }
  }

  /** Whether every suggested item is already listed. */
  get suggestionsListed(): boolean {
    return CHECKLIST_SUGGESTION_KEYS.every(key => this.listed(this.translate.instant(`steps.checklist.suggestions.${key}`)));
  }

  private listed(label: string): boolean {
    const wanted = label.trim().toLocaleLowerCase();
    return this.checklist.controls.some(item => (item.value.label as string).toLocaleLowerCase() === wanted);
  }

  private checklistItem(item: StepChecklistItem): FormGroup {
    return this.fb.group({
      label: [item.label, [Validators.required, Validators.maxLength(CHECKLIST_LABEL_MAX)]],
      done: [item.done]
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
    return `${toIsoDate(date)}T${pad(hours || 0)}:${pad(minutes || 0)}:00`;
  }

  static toTimeInput(date: Date): string {
    return `${date.getHours()}`.padStart(2, '0') + ':' + `${date.getMinutes()}`.padStart(2, '0');
  }
}
