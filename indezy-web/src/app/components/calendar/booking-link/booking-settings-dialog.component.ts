import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';

import { BookingSettings, WEEKDAYS, Weekday } from '../../../models';

/** The booking link's settings: on or off, its title, a call's length and buffer, notice, how far ahead, and weekly hours. */
@Component({
  selector: 'app-booking-settings-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatSlideToggleModule, TranslateModule],
  templateUrl: './booking-settings-dialog.component.html',
  styleUrls: ['./booking-settings-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class BookingSettingsDialogComponent {
  private readonly settings = inject<BookingSettings>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<BookingSettingsDialogComponent, BookingSettings>);
  private readonly fb = inject(FormBuilder);
  readonly durations = [15, 30, 45, 60];
  readonly buffers = [0, 10, 15, 30];
  readonly notices = [0, 4, 12, 24, 48];
  readonly horizons = [7, 14, 21, 30, 60];

  readonly form = this.fb.nonNullable.group({
    enabled: [this.settings.enabled],
    title: [this.settings.title || '', [Validators.required, Validators.maxLength(100)]],
    durationMinutes: [this.settings.durationMinutes],
    bufferMinutes: [this.settings.bufferMinutes],
    minNoticeHours: [this.settings.minNoticeHours],
    maxDaysAhead: [this.settings.maxDaysAhead],
    days: this.fb.array(WEEKDAYS.map(day => {
      const window = this.settings.windows.find(item => item.dayOfWeek === day);
      return this.fb.nonNullable.group({
        day: [day as Weekday],
        open: [!!window],
        start: [window?.start.substring(0, 5) ?? '09:00'],
        end: [window?.end.substring(0, 5) ?? '18:00']
      });
    }))
  });
  /** Which rule the hours break, if any. */
  hoursError: 'order' | 'none' | null = null;

  get days(): FormArray<FormGroup> {
    return this.form.controls.days as unknown as FormArray<FormGroup>;
  }

  save(): void {
    const value = this.form.getRawValue();
    const open = value.days.filter(day => day.open);
    this.hoursError = open.some(day => !day.start || !day.end || day.start >= day.end) ? 'order' :
      value.enabled && open.length === 0 ? 'none' : null;
    if (this.form.invalid || this.hoursError) {
      this.form.markAllAsTouched();
      return;
    }
    this.dialogRef.close({
      enabled: value.enabled, title: value.title.trim(), durationMinutes: value.durationMinutes, bufferMinutes: value.bufferMinutes,
      minNoticeHours: value.minNoticeHours, maxDaysAhead: value.maxDaysAhead,
      windows: open.map(day => ({ dayOfWeek: day.day, start: day.start, end: day.end }))
    });
  }
}
