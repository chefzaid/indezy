import { Component, Inject, ChangeDetectionStrategy } from '@angular/core';
import { AbstractControl, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { SeasonService } from '../../../services/season/season.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { Season } from '../../../models/season.models';
import { fromIsoDate, toIsoDate } from '../../../shared/locale/app-locale';

export interface SeasonDialogData {
  freelanceId: number;
  /** The season to edit; omitted when starting a new one. */
  season?: Season;
}

/** Calendar period of a date, used to suggest a season name ("Autumn search 2026"). */
export function periodOf(date: Date): 'winter' | 'spring' | 'summer' | 'autumn' {
  const month = date.getMonth();
  if (month < 2 || month === 11) {
    return 'winter';
  }
  if (month < 5) {
    return 'spring';
  }
  return month < 8 ? 'summer' : 'autumn';
}

/** Rejects an end date before the start date. */
export function endAfterStartValidator(group: AbstractControl): ValidationErrors | null {
  const start: Date | null = group.get('startDate')?.value;
  const end: Date | null = group.get('endDate')?.value;
  return start && end && end < start ? { endBeforeStart: true } : null;
}

/**
 * Starts or edits a job-hunting season: its name, dates (an open end means it is still running),
 * target daily rate and objective.
 */
@Component({
  selector: 'app-season-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatIconModule,
    TranslateModule
  ],
  templateUrl: './season-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./season-dialog.component.scss']
})
export class SeasonDialogComponent {
  readonly isEditMode: boolean;
  seasonForm: FormGroup;
  isSubmitting = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly dialogRef: MatDialogRef<SeasonDialogComponent, Season>,
    @Inject(MAT_DIALOG_DATA) public data: SeasonDialogData,
    private readonly seasonService: SeasonService,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService
  ) {
    const season = data.season;
    this.isEditMode = !!season?.id;
    const today = new Date();
    this.seasonForm = this.fb.group({
      name: [season?.name ?? this.suggestName(today), [Validators.required, Validators.maxLength(255)]],
      startDate: [season ? fromIsoDate(season.startDate) : today, Validators.required],
      endDate: [season?.endDate ? fromIsoDate(season.endDate) : null],
      targetDailyRate: [season?.targetDailyRate ?? null, [Validators.min(1)]],
      objective: [season?.objective ?? '']
    }, { validators: endAfterStartValidator });
  }

  onSubmit(): void {
    if (this.seasonForm.invalid || this.isSubmitting) {
      this.seasonForm.markAllAsTouched();
      return;
    }
    this.isSubmitting = true;
    const value = this.seasonForm.value;
    const payload: Season = {
      name: value.name.trim(),
      startDate: toIsoDate(value.startDate)!,
      endDate: toIsoDate(value.endDate) ?? null,
      targetDailyRate: value.targetDailyRate || null,
      objective: value.objective?.trim() || null,
      freelanceId: this.data.freelanceId
    };

    const request = this.isEditMode
      ? this.seasonService.update(this.data.season!.id!, payload)
      : this.seasonService.create(payload);

    request.subscribe({
      next: saved => {
        this.notificationService.success(this.isEditMode ? 'seasons.updatedSuccess' : 'seasons.createdSuccess');
        this.dialogRef.close(saved);
      },
      error: () => {
        this.notificationService.error('seasons.saveError');
        this.isSubmitting = false;
      }
    });
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  private suggestName(date: Date): string {
    return this.translate.instant('seasons.defaultName', {
      period: this.translate.instant('seasons.periods.' + periodOf(date)),
      year: date.getFullYear()
    });
  }
}
