import { ChangeDetectionStrategy, Component, LOCALE_ID, inject } from '@angular/core';
import { formatDate } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { TranslateModule } from '@ngx-translate/core';
import { APPLICATION_CHANNELS, ApplicationChannel, Offer, OfferApplicationRequest } from '../../../models/offer.models';
import { toIsoDate } from '../../../shared/locale/app-locale';

export interface OfferApplyDialogData {
  offer: Offer;
}

const DEFAULT_FOLLOW_UP_DAYS = 7;
const MAX_FOLLOW_UP_DAYS = 60;

/**
 * Records that the workspace applied to an offer: how, on which day (today at the latest) and whether to plan a
 * follow-up some days later. Closes with the request to send, without its language.
 */
@Component({
  selector: 'app-offer-apply-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatCheckboxModule, MatDatepickerModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatRadioModule, TranslateModule],
  templateUrl: './offer-apply-dialog.component.html',
  styleUrl: './offer-apply-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferApplyDialogComponent {
  readonly data: OfferApplyDialogData = inject(MAT_DIALOG_DATA);
  readonly channels = APPLICATION_CHANNELS;
  readonly maxFollowUpDays = MAX_FOLLOW_UP_DAYS;
  readonly today = startOfDay(new Date());

  private readonly dialogRef: MatDialogRef<OfferApplyDialogComponent, OfferApplicationRequest> = inject(MatDialogRef);
  private readonly locale = inject(LOCALE_ID);

  readonly form = inject(FormBuilder).nonNullable.group({
    via: ['SITE_FORM' as ApplicationChannel, Validators.required],
    appliedOn: [this.today, Validators.required],
    followUp: [true],
    followUpInDays: [DEFAULT_FOLLOW_UP_DAYS, [Validators.required, Validators.min(1), Validators.max(MAX_FOLLOW_UP_DAYS)]]
  });

  constructor() {
    this.form.controls.followUp.valueChanges.subscribe(followUp => {
      const days = this.form.controls.followUpInDays;
      if (followUp) {
        days.enable();
      } else {
        days.disable();
      }
    });
  }

  /** The day the follow-up is planned on, as the server plans it: tomorrow at the earliest. */
  get followUpDay(): string | null {
    const { appliedOn, followUpInDays } = this.form.getRawValue();
    if (!appliedOn || !this.form.controls.followUpInDays.valid) {
      return null;
    }
    const day = startOfDay(appliedOn);
    day.setDate(day.getDate() + followUpInDays);
    const tomorrow = new Date(this.today);
    tomorrow.setDate(tomorrow.getDate() + 1);
    return formatDate(day > this.today ? day : tomorrow, 'fullDate', this.locale);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { via, appliedOn, followUp, followUpInDays } = this.form.getRawValue();
    this.dialogRef.close({ via, appliedOn: toIsoDate(appliedOn) ?? '', ...(followUp ? { followUpInDays } : {}) });
  }
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}
