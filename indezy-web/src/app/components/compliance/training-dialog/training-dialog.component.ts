import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { FUNDING_STATUSES, FundingStatus, TRAINING_FUNDS, Training, TrainingDetails, TrainingFund } from '../../../models';

const AMOUNT = Validators.pattern(/^\d+([.,]\d{1,2})?$/);

/** Records a training or changes it: what and when, the certification it leads to, and the funding asked for it. */
@Component({
  selector: 'app-training-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, TranslateModule],
  templateUrl: './training-dialog.component.html',
  styleUrls: ['../document-dialog/compliance-document-dialog.component.scss'],
  // A hint under a field takes its own line, clear of the next field's label.
  styles: '[subscriptsizing="dynamic"] { margin-bottom: 8px; }',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class TrainingDialogComponent {
  /** The training to change; none to record a new one. */
  readonly training = inject<Training | null>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<TrainingDialogComponent, TrainingDetails>);
  readonly funds = TRAINING_FUNDS;
  readonly statuses = FUNDING_STATUSES;

  readonly form = inject(FormBuilder).group({
    title: [this.training?.title ?? '', [Validators.required, Validators.maxLength(200)]],
    provider: [this.training?.provider ?? '', Validators.maxLength(200)],
    startsOn: [this.training?.startsOn ?? ''],
    endsOn: [this.training?.endsOn ?? ''],
    hours: [this.training?.hours ?? null as number | null, [Validators.min(0), Validators.max(10_000), Validators.pattern(/^\d+$/)]],
    cost: [this.training?.cost?.toString() ?? '', AMOUNT],
    certification: [this.training?.certification ?? '', Validators.maxLength(200)],
    certifiedOn: [this.training?.certifiedOn ?? ''],
    certificationExpiresOn: [this.training?.certificationExpiresOn ?? ''],
    fund: [this.training?.fund ?? null as TrainingFund | null],
    fundingStatus: [this.training?.fundingStatus ?? 'TO_REQUEST' as FundingStatus],
    fundingDeadline: [this.training?.fundingDeadline ?? ''],
    fundedAmount: [this.training?.fundedAmount?.toString() ?? '', AMOUNT],
    notes: [this.training?.notes ?? '', Validators.maxLength(1000)]
  }, { validators: control => TrainingDialogComponent.endsAfterStart(control) });

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const text = (input: string | null | undefined): string | null => input?.trim() || null;
    const amount = (input: string | null | undefined): number | null => text(input) === null ? null : Number(text(input)!.replace(',', '.'));
    const funded = !!value.fund;
    this.dialogRef.close({
      title: value.title!.trim(),
      provider: text(value.provider),
      startsOn: value.startsOn || null,
      endsOn: value.endsOn || null,
      hours: value.hours === null || `${value.hours}` === '' ? null : Number(value.hours),
      cost: amount(value.cost),
      certification: text(value.certification),
      certifiedOn: value.certifiedOn || null,
      certificationExpiresOn: value.certificationExpiresOn || null,
      fund: value.fund ?? null,
      fundingStatus: funded ? value.fundingStatus : null,
      fundingDeadline: funded ? value.fundingDeadline || null : null,
      fundedAmount: funded ? amount(value.fundedAmount) : null,
      notes: text(value.notes)
    });
  }

  /** A training ends on or after its start. */
  private static endsAfterStart(control: AbstractControl): ValidationErrors | null {
    const { startsOn, endsOn } = control.value;
    return startsOn && endsOn && endsOn < startsOn ? { endsBeforeStart: true } : null;
  }
}
