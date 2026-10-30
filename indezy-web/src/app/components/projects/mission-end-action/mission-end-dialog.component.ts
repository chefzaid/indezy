import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

import { fromIsoDate, toIsoDate } from '../../../shared/locale/app-locale';

export interface MissionEndDialogData {
  role: string;
  /** The mission's start (yyyy-MM-dd): it cannot end before. */
  startDate?: string;
}

/** The day a mission ended, and why. */
export interface MissionEnd {
  endedOn: string;
  reason: string;
}

/** Asks when a mission ended, today by default, and why; closes with both, or with nothing when cancelled. */
@Component({
  selector: 'app-mission-end-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDatepickerModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'missionEnd.dialogTitle' | translate }}</h2>
    <mat-dialog-content>
      <p class="role">{{ data.role }}</p>
      <form [formGroup]="form" (ngSubmit)="confirm()" id="mission-end-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'missionEnd.date' | translate }}</mat-label>
          <input matInput [matDatepicker]="picker" formControlName="endedOn" [min]="minDate" required data-mission-end-date>
          <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
          <mat-datepicker #picker></mat-datepicker>
          @if (form.get('endedOn')?.hasError('matDatepickerMin')) {
            <mat-error>{{ 'missionEnd.beforeStart' | translate }}</mat-error>
          } @else if (form.get('endedOn')?.invalid) {
            <mat-error>{{ 'errors.fieldRequired' | translate }}</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>{{ 'missionEnd.reason' | translate }}</mat-label>
          <textarea matInput formControlName="reason" rows="4" maxlength="2000" required data-mission-end-reason></textarea>
          <mat-hint>{{ 'missionEnd.reasonHint' | translate }}</mat-hint>
          @if (form.get('reason')?.touched && form.get('reason')?.invalid) {
            <mat-error>{{ 'errors.fieldRequired' | translate }}</mat-error>
          }
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      <button mat-raised-button color="primary" type="submit" form="mission-end-form" [disabled]="form.invalid" data-mission-end-confirm>
        {{ 'missionEnd.confirm' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .role { margin: 0 0 12px; font-weight: 600; color: var(--app-text); }
    .full-width { width: 100%; }
    form { display: flex; flex-direction: column; gap: 4px; min-width: min(420px, 70vw); }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MissionEndDialogComponent {
  readonly minDate: Date | null;
  readonly form: FormGroup;

  constructor(
    fb: FormBuilder,
    private readonly dialogRef: MatDialogRef<MissionEndDialogComponent, MissionEnd>,
    @Inject(MAT_DIALOG_DATA) public data: MissionEndDialogData
  ) {
    this.minDate = fromIsoDate(data.startDate);
    this.form = fb.group({
      endedOn: [new Date(), Validators.required],
      // A reason of spaces only says nothing.
      reason: ['', [Validators.required, Validators.pattern(/\S/)]]
    });
  }

  confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.dialogRef.close({ endedOn: toIsoDate(this.form.value.endedOn)!, reason: this.form.value.reason.trim() });
  }
}
